package io.jenkins.plugins.dorametrics.ui;

import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class DoraDashboardActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void extensionRegistered() {
        DoraDashboardAction action = Jenkins.get().getExtensionList(RootAction.class)
                .get(DoraDashboardAction.class);
        assertNotNull("DoraDashboardAction should be registered", action);
        assertEquals("dora-metrics", action.getUrlName());
        assertEquals("DORA Metrics", action.getDisplayName());
        assertNotNull(action.getIconFileName());
    }

    @Test
    public void doraMetricsNotNull() {
        DoraDashboardAction action = new DoraDashboardAction();
        assertNotNull(action.getDeploymentFrequency());
        assertNotNull(action.getLeadTime());
        assertNotNull(action.getMttr());
        assertNotNull(action.getChangeFailureRate());
    }

    @Test
    public void rankingsNotNull() {
        DoraDashboardAction action = new DoraDashboardAction();
        assertNotNull(action.getSlowestPipelines());
        assertNotNull(action.getMostFailingPipelines());
        assertNotNull(action.getMostImprovedPipelines());
        assertNotNull(action.getFlakiestPipelines());
        assertNotNull(action.getSlowestStages());
        assertNotNull(action.getMostFailingStages());
    }

    @Test
    public void jobUrlConvertsCorrectly() {
        DoraDashboardAction action = new DoraDashboardAction();
        assertEquals("job/production/job/api-gateway", action.jobUrl("production/api-gateway"));
        assertEquals("job/simple-job", action.jobUrl("simple-job"));
        assertEquals("job/a/job/b/job/c", action.jobUrl("a/b/c"));
        assertEquals("", action.jobUrl(null));
    }
}
