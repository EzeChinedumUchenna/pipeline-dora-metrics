package io.jenkins.plugins.dorametrics.collectors;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.scm.ChangeLogSet;
import io.jenkins.plugins.dorametrics.DoraGlobalConfiguration;
import io.jenkins.plugins.dorametrics.store.MetricsStore;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens to all build completions and captures metrics into the H2 database.
 */
@Extension
public class BuildDataCollector extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(BuildDataCollector.class.getName());

    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        try {
            collectBuildData(run);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to collect metrics for " + run.getFullDisplayName(), e);
        }
    }

    private void collectBuildData(Run<?, ?> run) {
        DoraGlobalConfiguration config = DoraGlobalConfiguration.get();
        String jobName = run.getParent().getFullName();

        if (config != null && !config.shouldTrackJob(jobName)) {
            return;
        }

        MetricsStore store = MetricsStore.getInstance();
        int buildNumber = run.getNumber();
        long timestamp = run.getTimeInMillis();
        long durationMs = run.getDuration();
        String result = run.getResult() != null ? run.getResult().toString() : "UNKNOWN";
        String triggerType = getTriggerType(run);
        String branch = getBranch(run);

        long buildId = store.insertBuild(jobName, buildNumber, timestamp, durationMs, result, triggerType, branch);
        if (buildId < 0) return;

        collectCommitData(run, buildId, store);

        if (run instanceof WorkflowRun) {
            collectStageData((WorkflowRun) run, buildId, store);
        }

        LOGGER.fine("Collected metrics for " + jobName + "#" + buildNumber
                + " (" + result + ", " + durationMs + "ms)");
    }

    private void collectCommitData(Run<?, ?> run, long buildId, MetricsStore store) {
        try {
            for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : getChangeSets(run)) {
                for (ChangeLogSet.Entry entry : changeSet) {
                    store.insertCommit(buildId, entry.getCommitId(),
                            entry.getAuthor().getFullName(), entry.getTimestamp());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not collect commit data for " + run.getFullDisplayName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets(Run<?, ?> run) {
        if (run instanceof WorkflowRun) {
            return ((WorkflowRun) run).getChangeSets();
        }
        if (run instanceof hudson.model.AbstractBuild) {
            ChangeLogSet<? extends ChangeLogSet.Entry> cs = ((hudson.model.AbstractBuild<?, ?>) run).getChangeSet();
            if (cs != null && !cs.isEmptySet()) {
                return Collections.singletonList(cs);
            }
        }
        return Collections.emptyList();
    }

    private void collectStageData(WorkflowRun run, long buildId, MetricsStore store) {
        try {
            if (run.getExecution() == null) return;

            DepthFirstScanner scanner = new DepthFirstScanner();
            List<FlowNode> allNodes = new ArrayList<>();
            scanner.setup(run.getExecution().getCurrentHeads());
            scanner.forEach(allNodes::add);

            Map<String, FlowNode> stageStarts = new LinkedHashMap<>();
            Map<String, FlowNode> stageEnds = new LinkedHashMap<>();

            for (FlowNode node : allNodes) {
                if (node instanceof StepStartNode startNode) {
                    LabelAction labelAction = node.getAction(LabelAction.class);
                    if (labelAction != null) {
                        if (node.getAction(org.jenkinsci.plugins.workflow.actions.ThreadNameAction.class) != null
                                || startNode.getDescriptor() == null
                                || "stage".equals(startNode.getDescriptor().getFunctionName())) {
                            stageStarts.putIfAbsent(labelAction.getDisplayName(), node);
                        }
                    }
                } else if (node instanceof StepEndNode) {
                    FlowNode start = ((StepEndNode) node).getStartNode();
                    LabelAction labelAction = start.getAction(LabelAction.class);
                    if (labelAction != null) {
                        stageEnds.putIfAbsent(labelAction.getDisplayName(), node);
                    }
                }
            }

            for (Map.Entry<String, FlowNode> entry : stageStarts.entrySet()) {
                String stageName = entry.getKey();
                FlowNode startNode = entry.getValue();
                FlowNode endNode = stageEnds.get(stageName);
                if (endNode == null) continue;

                TimingAction startTiming = startNode.getAction(TimingAction.class);
                TimingAction endTiming = endNode.getAction(TimingAction.class);
                if (startTiming == null || endTiming == null) continue;

                long duration = Math.max(0, endTiming.getStartTime() - startTiming.getStartTime());
                boolean hasError = endNode.getAction(ErrorAction.class) != null;

                store.insertStage(buildId, stageName, duration, hasError ? "FAILURE" : "SUCCESS");
            }

            LOGGER.fine("Collected " + stageStarts.size() + " stages for " + run.getFullDisplayName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not collect stage data for " + run.getFullDisplayName(), e);
        }
    }

    private String getBranch(Run<?, ?> run) {
        try {
            hudson.EnvVars env = run.getEnvironment(hudson.model.TaskListener.NULL);
            String[] branchVars = {"BRANCH_NAME", "GIT_BRANCH", "GIT_LOCAL_BRANCH",
                    "SVN_BRANCH", "CHANGE_BRANCH", "BRANCH"};
            for (String var : branchVars) {
                String branch = env.get(var);
                if (branch != null && !branch.isEmpty()) {
                    if (branch.contains("/")) {
                        branch = branch.substring(branch.lastIndexOf('/') + 1);
                    }
                    return branch;
                }
            }
            // Multibranch: job name often IS the branch
            String jobName = run.getParent().getName();
            String fullName = run.getParent().getFullName();
            if (fullName.contains("/") && !fullName.equals(jobName)) {
                return jobName;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not determine branch for " + run.getFullDisplayName(), e);
        }
        return null;
    }

    private String getTriggerType(Run<?, ?> run) {
        List<Cause> causes = run.getCauses();
        if (causes.isEmpty()) return "UNKNOWN";
        Cause cause = causes.get(0);
        if (cause instanceof Cause.UserIdCause) return "USER";
        if (cause instanceof Cause.UpstreamCause) return "UPSTREAM";
        String className = cause.getClass().getSimpleName();
        if (className.contains("Timer")) return "TIMER";
        if (className.contains("SCM")) return "SCM";
        return className;
    }
}
