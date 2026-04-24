package io.jenkins.plugins.dorametrics.ui;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.dorametrics.DoraGlobalConfiguration;
import io.jenkins.plugins.dorametrics.dora.DoraCalculator;
import io.jenkins.plugins.dorametrics.dora.DoraCalculator.DoraMetric;
import io.jenkins.plugins.dorametrics.rankings.PipelineRanker;
import io.jenkins.plugins.dorametrics.rankings.PipelineRanker.RankedPipeline;
import io.jenkins.plugins.dorametrics.rankings.PipelineRanker.RankedStage;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * Dashboard UI at /dora-metrics/. Data methods called from Jelly template.
 * API endpoints are handled by {@link DoraApiAction} at /dora-api/.
 */
@Extension
public class DoraDashboardAction implements RootAction {

    @Override
    public String getIconFileName() { return "graph.png"; }

    @Override
    public String getDisplayName() { return "DORA Metrics"; }

    @Override
    public String getUrlName() { return "dora-metrics"; }

    // === Dashboard data for Jelly ===

    public DoraMetric getDeploymentFrequency() {
        return new DoraCalculator().deploymentFrequency(thirtyDaysAgo(), now(), getPattern());
    }

    public DoraMetric getLeadTime() {
        return new DoraCalculator().leadTimeForChanges(thirtyDaysAgo(), now(), getPattern());
    }

    public DoraMetric getMttr() {
        return new DoraCalculator().meanTimeToRestore(thirtyDaysAgo(), now(), getPattern());
    }

    public DoraMetric getChangeFailureRate() {
        return new DoraCalculator().changeFailureRate(thirtyDaysAgo(), now(), getPattern());
    }

    public List<RankedPipeline> getSlowestPipelines() {
        return new PipelineRanker().slowestPipelines(thirtyDaysAgo(), now(), getTopN());
    }

    public List<RankedPipeline> getMostFailingPipelines() {
        return new PipelineRanker().mostFailingPipelines(thirtyDaysAgo(), now(), getTopN());
    }

    public List<RankedPipeline> getMostImprovedPipelines() {
        long n = now();
        long ago = thirtyDaysAgo();
        return new PipelineRanker().mostImproved(ago, n, ago - (30L * 86400_000), ago, getTopN());
    }

    public List<RankedPipeline> getFlakiestPipelines() {
        return new PipelineRanker().flakiestPipelines(thirtyDaysAgo(), now(), getTopN());
    }

    public List<RankedStage> getSlowestStages() {
        return new PipelineRanker().slowestStages(thirtyDaysAgo(), now(), getTopN());
    }

    public List<RankedStage> getMostFailingStages() {
        return new PipelineRanker().mostFailingStages(thirtyDaysAgo(), now(), getTopN());
    }

    /**
     * Convert job full name to Jenkins URL path for drill-down links.
     * e.g. "production/api-gateway" -> "job/production/job/api-gateway"
     */
    public String jobUrl(String jobName) {
        if (jobName == null) return "";
        return "job/" + jobName.replace("/", "/job/");
    }

    // === Helpers ===

    private String getPattern() {
        DoraGlobalConfiguration config = DoraGlobalConfiguration.get();
        return config != null ? config.getProductionJobPattern() : ".*";
    }

    private int getTopN() {
        DoraGlobalConfiguration config = DoraGlobalConfiguration.get();
        return config != null ? config.getDashboardTopN() : 10;
    }

    private long now() { return System.currentTimeMillis(); }

    private long thirtyDaysAgo() { return now() - (30L * 86400_000); }
}
