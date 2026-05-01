package io.jenkins.plugins.dorametrics.store;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import io.jenkins.plugins.dorametrics.DoraGlobalConfiguration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodic task that runs every hour to check if export or cleanup is needed.
 * Export interval is configurable (default: every 24 hours).
 * Cleanup removes data older than retentionDays.
 */
@Extension
public class MetricsMaintenanceTask extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(MetricsMaintenanceTask.class.getName());
    private static final long HOUR_MS = 3600_000;
    private long lastExportTime = 0;

    public MetricsMaintenanceTask() {
        super("DORA Metrics Maintenance");
    }

    @Override
    public long getRecurrencePeriod() {
        return HOUR_MS; // check every hour
    }

    @Override
    protected void execute(TaskListener listener) {
        try {
            DoraGlobalConfiguration config = DoraGlobalConfiguration.get();
            if (config == null) return;

            // Cleanup old data
            long retainAfter = System.currentTimeMillis()
                    - ((long) config.getRetentionDays() * 86400_000);
            MetricsStore.getInstance().cleanup(retainAfter);

            // Export if enabled and interval has elapsed
            if (config.isExportEnabled()) {
                int intervalHours = Math.max(1, config.getExportIntervalHours());
                long intervalMs = (long) intervalHours * HOUR_MS;

                if (System.currentTimeMillis() - lastExportTime >= intervalMs) {
                    MetricsExporter.exportDailySnapshot();
                    lastExportTime = System.currentTimeMillis();
                    LOGGER.info("DORA metrics export completed");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "DORA metrics maintenance failed", e);
        }
    }
}
