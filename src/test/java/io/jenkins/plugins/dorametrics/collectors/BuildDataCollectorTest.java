package io.jenkins.plugins.dorametrics.collectors;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.dorametrics.store.MetricsStore;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.*;

public class BuildDataCollectorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        MetricsStore.setInstance(null);
    }

    @Test
    public void runListenerRegistered() {
        BuildDataCollector collector = Jenkins.get().getExtensionList(RunListener.class)
                .get(BuildDataCollector.class);
        assertNotNull("BuildDataCollector should be registered as RunListener", collector);
    }

    @Test
    public void capturesFreestyleBuild() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("freestyle-test");
        FreeStyleBuild build = j.buildAndAssertSuccess(job);

        MetricsStore store = MetricsStore.getInstance();
        long now = System.currentTimeMillis();
        List<MetricsStore.BuildRecord> builds = store.getBuilds("freestyle-test", now - 60000, now + 1000);
        assertEquals("Should capture freestyle build", 1, builds.size());
        assertEquals("freestyle-test", builds.get(0).jobName);
        assertEquals("SUCCESS", builds.get(0).result);
        assertNotNull("Trigger type should not be null", builds.get(0).triggerType);
    }

    @Test
    public void capturesMultipleBuilds() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("multi-test");
        j.buildAndAssertSuccess(job);
        j.buildAndAssertSuccess(job);
        j.buildAndAssertSuccess(job);

        MetricsStore store = MetricsStore.getInstance();
        long now = System.currentTimeMillis();
        List<MetricsStore.BuildRecord> builds = store.getBuilds("multi-test", now - 60000, now + 1000);
        assertEquals("Should capture 3 builds", 3, builds.size());
        assertEquals(1, builds.get(0).buildNumber);
        assertEquals(2, builds.get(1).buildNumber);
        assertEquals(3, builds.get(2).buildNumber);
    }

    @Test
    public void buildDurationIsPositive() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("duration-test");
        j.buildAndAssertSuccess(job);

        MetricsStore store = MetricsStore.getInstance();
        long now = System.currentTimeMillis();
        List<MetricsStore.BuildRecord> builds = store.getBuilds("duration-test", now - 60000, now + 1000);
        assertTrue("Duration should be positive", builds.get(0).durationMs >= 0);
    }
}
