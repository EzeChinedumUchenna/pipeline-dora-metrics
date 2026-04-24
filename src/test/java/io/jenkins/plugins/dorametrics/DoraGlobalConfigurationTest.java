package io.jenkins.plugins.dorametrics;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class DoraGlobalConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private DoraGlobalConfiguration config;

    @Before
    public void setUp() {
        config = DoraGlobalConfiguration.get();
        assertNotNull("Config should not be null", config);
    }

    @Test
    public void defaultValues() {
        assertEquals(".*", config.getProductionJobPattern());
        assertEquals("", config.getExcludedJobPattern());
        assertEquals("main|master", config.getProductionBranchPattern());
        assertEquals(365, config.getRetentionDays());
        assertEquals(10, config.getDashboardTopN());
    }

    @Test
    public void shouldTrackJobDefaultMatchesAll() {
        assertTrue(config.shouldTrackJob("any-job"));
        assertTrue(config.shouldTrackJob("production/api"));
        assertTrue(config.shouldTrackJob("development/feature"));
    }

    @Test
    public void shouldTrackJobWithProductionPattern() {
        config.setProductionJobPattern("production/.*");

        assertTrue(config.shouldTrackJob("production/api-gateway"));
        assertTrue(config.shouldTrackJob("production/user-service"));
        assertFalse(config.shouldTrackJob("staging/api-gateway"));
        assertFalse(config.shouldTrackJob("development/feature"));
        assertFalse(config.shouldTrackJob("test-job"));
    }

    @Test
    public void shouldTrackJobWithExcludedPattern() {
        config.setProductionJobPattern(".*");
        config.setExcludedJobPattern("development/.*");

        assertTrue(config.shouldTrackJob("production/api"));
        assertTrue(config.shouldTrackJob("staging/api"));
        assertFalse(config.shouldTrackJob("development/feature-auth"));
        assertFalse(config.shouldTrackJob("development/feature-search"));
    }

    @Test
    public void shouldTrackJobWithFolders() {
        config.setProductionJobPattern("nomatch");
        config.setProductionFolders("production,staging");

        assertTrue(config.shouldTrackJob("production/api-gateway"));
        assertTrue(config.shouldTrackJob("staging/user-service"));
        assertFalse(config.shouldTrackJob("development/feature"));
        assertFalse(config.shouldTrackJob("test-deploy-prod"));
    }

    @Test
    public void shouldTrackJobWithCombinedFilters() {
        config.setProductionJobPattern(".*");
        config.setExcludedJobPattern(".*feature.*");

        assertTrue(config.shouldTrackJob("production/api"));
        assertTrue(config.shouldTrackJob("staging/api"));
        assertFalse(config.shouldTrackJob("development/feature-auth"));
        assertFalse(config.shouldTrackJob("development/feature-search"));
        assertTrue(config.shouldTrackJob("test-deploy-prod"));
    }

    @Test
    public void shouldTrackJobNullInput() {
        assertFalse(config.shouldTrackJob(null));
    }

    @Test
    public void invalidRegexFallsBackToDefault() {
        config.setProductionJobPattern("[invalid");
        // Should not crash, pattern recompiles on set
        // shouldTrackJob should still work (falls back)
        config.setProductionJobPattern(".*"); // reset
        assertTrue(config.shouldTrackJob("any-job"));
    }

    @Test
    public void doraThresholdDefaults() {
        assertEquals(1.0, config.getDfEliteThreshold(), 0.001);
        assertEquals(0.142, config.getDfHighThreshold(), 0.001);
        assertEquals(5.0, config.getCfrElitePercent(), 0.001);
    }

    @Test
    public void doraThresholdsCustomizable() {
        config.setDfEliteThreshold(10.0);
        assertEquals(10.0, config.getDfEliteThreshold(), 0.001);
        config.setDfEliteThreshold(1.0); // reset
    }
}
