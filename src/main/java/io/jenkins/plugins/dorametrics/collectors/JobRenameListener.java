package io.jenkins.plugins.dorametrics.collectors;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import io.jenkins.plugins.dorametrics.store.MetricsStore;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks job renames and moves so stored metrics data stays linked
 * to the current job name.
 */
@Extension
public class JobRenameListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(JobRenameListener.class.getName());

    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        try {
            MetricsStore.getInstance().renameJob(oldFullName, newFullName);
            LOGGER.info("DORA Metrics: renamed job data from " + oldFullName + " to " + newFullName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to rename job metrics: " + oldFullName + " -> " + newFullName, e);
        }
    }
}
