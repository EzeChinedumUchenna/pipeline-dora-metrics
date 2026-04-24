package io.jenkins.plugins.dorametrics.ui;

import hudson.Extension;
import hudson.model.ManagementLink;

/**
 * Adds "DORA Metrics" link to the Jenkins sidebar under "Manage Jenkins".
 */
@Extension
public class DoraDashboardLink extends ManagementLink {

    @Override
    public String getIconFileName() {
        return "graph.png";
    }

    @Override
    public String getDisplayName() {
        return "DORA Metrics";
    }

    @Override
    public String getUrlName() {
        return "dora-metrics";
    }

    @Override
    public String getDescription() {
        return "View DORA metrics, pipeline analytics, rankings, and performance trends.";
    }

    @Override
    public Category getCategory() {
        return Category.STATUS;
    }
}
