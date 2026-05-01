package io.jenkins.plugins.dorametrics.ui;

import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import io.jenkins.plugins.dorametrics.store.MetricsStore;

import java.util.List;

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

    @Before
    public void setUp() {
        MetricsStore.setInstance(null);
    }

    private WebClient noJsClient() {
        WebClient wc = j.createWebClient();
        wc.setJavaScriptEnabled(false);
        return wc;
    }

    @Test
    public void dashboardPageLoads() throws Exception {
        HtmlPage page = noJsClient().goTo("dora-metrics");
        assertNotNull("Dashboard page should load", page);
        assertEquals(200, page.getWebResponse().getStatusCode());
    }

    @Test
    public void tablesHaveSmallClass() throws Exception {
        HtmlPage page = noJsClient().goTo("dora-metrics");
        List<DomElement> tables = page.getByXPath("//table[contains(@class, 'jenkins-table')]");
        assertFalse("Dashboard should have tables", tables.isEmpty());
        for (DomElement table : tables) {
            String classes = table.getAttribute("class");
            assertTrue("Table should have jenkins-table--small: " + classes,
                    classes.contains("jenkins-table--small"));
        }
    }

    @Test
    public void activeButtonHasPrimaryClass() throws Exception {
        HtmlPage page = noJsClient().goTo("dora-metrics");
        // The default active button (30d) should have jenkins-button--primary
        List<DomElement> primaryBtns = page.getByXPath(
                "//button[contains(@class, 'dora-date-btn') and contains(@class, 'jenkins-button--primary')]");
        assertEquals("Exactly one date button should be primary", 1, primaryBtns.size());
        assertEquals("30", primaryBtns.get(0).getAttribute("data-days"));

        // All other date buttons should be tertiary
        List<DomElement> tertiaryBtns = page.getByXPath(
                "//button[contains(@class, 'dora-date-btn') and contains(@class, 'jenkins-button--tertiary')]");
        assertEquals("Four date buttons should be tertiary", 4, tertiaryBtns.size());
    }

    @Test
    public void dashboardHasDoraSections() throws Exception {
        HtmlPage page = noJsClient().goTo("dora-metrics");
        List<DomElement> sections = page.getByXPath("//*[contains(@class, 'dora-toggle')]");
        assertTrue("Dashboard should have collapsible sections", sections.size() >= 3);
    }
}
