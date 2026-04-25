package io.jenkins.plugins.dorametrics;

import hudson.Extension;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Global configuration for the Pipeline DORA Metrics plugin.
 */
@Extension
public class DoraGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(DoraGlobalConfiguration.class.getName());

    private String productionJobPattern = ".*";
    private String excludedJobPattern = "";
    private String productionBranchPattern = "main|master";
    private String productionFolders = "";
    private String productionJobLabel = "dora-production";
    private boolean trackAllBranches = true;
    private int retentionDays = 365;
    private int dashboardTopN = 10;

    // External storage export
    private String exportStorageType = "LOCAL";
    private String exportEndpoint = "";
    private Secret exportAccessKey;
    private Secret exportSecretKey;
    private String exportBucket = "";
    private boolean exportEnabled = false;

    // DORA band thresholds
    private double dfEliteThreshold = 1.0;
    private double dfHighThreshold = 0.142;
    private double dfMediumThreshold = 0.033;
    private double ltEliteMs = 3600000;
    private double ltHighMs = 86400000;
    private double ltMediumMs = 604800000;
    private double mttrEliteMs = 3600000;
    private double mttrHighMs = 86400000;
    private double mttrMediumMs = 604800000;
    private double cfrElitePercent = 5.0;
    private double cfrHighPercent = 10.0;
    private double cfrMediumPercent = 15.0;

    // Pre-compiled patterns for performance
    private transient Pattern compiledProductionPattern;
    private transient Pattern compiledExcludedPattern;

    public DoraGlobalConfiguration() {
        load();
        compilePatterns();
    }

    public static DoraGlobalConfiguration get() {
        return GlobalConfiguration.all().get(DoraGlobalConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        this.productionJobPattern = validateRegex(json.optString("productionJobPattern", ".*"), ".*");
        this.excludedJobPattern = validateRegex(json.optString("excludedJobPattern", ""), "");
        this.productionBranchPattern = validateRegex(json.optString("productionBranchPattern", "main|master"), "main|master");
        this.productionFolders = json.optString("productionFolders", "");
        this.productionJobLabel = json.optString("productionJobLabel", "dora-production");
        this.trackAllBranches = json.optBoolean("trackAllBranches", true);
        this.retentionDays = json.optInt("retentionDays", 365);
        this.dashboardTopN = json.optInt("dashboardTopN", 10);

        this.exportStorageType = json.optString("exportStorageType", "LOCAL");
        this.exportEndpoint = json.optString("exportEndpoint", "");
        this.exportAccessKey = Secret.fromString(json.optString("exportAccessKey", ""));
        this.exportSecretKey = Secret.fromString(json.optString("exportSecretKey", ""));
        this.exportBucket = json.optString("exportBucket", "");
        this.exportEnabled = json.optBoolean("exportEnabled", false);

        this.dfEliteThreshold = json.optDouble("dfEliteThreshold", 1.0);
        this.dfHighThreshold = json.optDouble("dfHighThreshold", 0.142);
        this.dfMediumThreshold = json.optDouble("dfMediumThreshold", 0.033);
        this.ltEliteMs = json.optDouble("ltEliteMs", 3600000);
        this.ltHighMs = json.optDouble("ltHighMs", 86400000);
        this.ltMediumMs = json.optDouble("ltMediumMs", 604800000);
        this.mttrEliteMs = json.optDouble("mttrEliteMs", 3600000);
        this.mttrHighMs = json.optDouble("mttrHighMs", 86400000);
        this.mttrMediumMs = json.optDouble("mttrMediumMs", 604800000);
        this.cfrElitePercent = json.optDouble("cfrElitePercent", 5.0);
        this.cfrHighPercent = json.optDouble("cfrHighPercent", 10.0);
        this.cfrMediumPercent = json.optDouble("cfrMediumPercent", 15.0);

        compilePatterns();
        save();
        return true;
    }

    private String validateRegex(String pattern, String fallback) {
        if (pattern == null || pattern.isEmpty()) return fallback;
        try {
            Pattern.compile(pattern);
            return pattern;
        } catch (PatternSyntaxException e) {
            LOGGER.log(Level.WARNING, "Invalid regex pattern: " + pattern, e);
            return fallback;
        }
    }

    private void compilePatterns() {
        try {
            compiledProductionPattern = (productionJobPattern != null && !productionJobPattern.isEmpty())
                    ? Pattern.compile(productionJobPattern) : null;
        } catch (PatternSyntaxException e) {
            compiledProductionPattern = null;
        }
        try {
            compiledExcludedPattern = (excludedJobPattern != null && !excludedJobPattern.isEmpty())
                    ? Pattern.compile(excludedJobPattern) : null;
        } catch (PatternSyntaxException e) {
            compiledExcludedPattern = null;
        }
    }

    /**
     * Check if a job should be tracked based on all filter criteria.
     * Uses pre-compiled patterns for performance.
     */
    public boolean shouldTrackJob(String fullJobName) {
        if (fullJobName == null) return false;

        if (compiledExcludedPattern == null && excludedJobPattern != null && !excludedJobPattern.isEmpty()) {
            compilePatterns();
        }

        if (compiledExcludedPattern != null && compiledExcludedPattern.matcher(fullJobName).matches()) {
            return false;
        }

        if (productionFolders != null && !productionFolders.isEmpty()) {
            String[] folders = productionFolders.split(",");
            for (String folder : folders) {
                if (fullJobName.startsWith(folder.trim() + "/")) {
                    return true;
                }
            }
        }

        if (compiledProductionPattern == null && productionJobPattern != null && !productionJobPattern.isEmpty()) {
            compilePatterns();
        }

        if (compiledProductionPattern != null) {
            return compiledProductionPattern.matcher(fullJobName).matches();
        }

        return true;
    }

    // --- Getters/Setters ---

    public String getProductionJobPattern() { return productionJobPattern; }
    public void setProductionJobPattern(String v) { this.productionJobPattern = v; compilePatterns(); }

    public String getExcludedJobPattern() { return excludedJobPattern; }
    public void setExcludedJobPattern(String v) { this.excludedJobPattern = v; compilePatterns(); }

    public String getProductionBranchPattern() { return productionBranchPattern; }
    public void setProductionBranchPattern(String v) { this.productionBranchPattern = v; }

    public String getProductionFolders() { return productionFolders; }
    public void setProductionFolders(String v) { this.productionFolders = v; }

    public String getProductionJobLabel() { return productionJobLabel; }
    public void setProductionJobLabel(String v) { this.productionJobLabel = v; }

    public boolean isTrackAllBranches() { return trackAllBranches; }
    public void setTrackAllBranches(boolean v) { this.trackAllBranches = v; }

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int v) { this.retentionDays = v; }

    public int getDashboardTopN() { return dashboardTopN; }
    public void setDashboardTopN(int v) { this.dashboardTopN = v; }

    public String getExportStorageType() { return exportStorageType; }
    public void setExportStorageType(String v) { this.exportStorageType = v; }

    public String getExportEndpoint() { return exportEndpoint; }
    public void setExportEndpoint(String v) { this.exportEndpoint = v; }

    public Secret getExportAccessKey() { return exportAccessKey; }
    public void setExportAccessKey(Secret v) { this.exportAccessKey = v; }

    public Secret getExportSecretKey() { return exportSecretKey; }
    public void setExportSecretKey(Secret v) { this.exportSecretKey = v; }

    public String getExportBucket() { return exportBucket; }
    public void setExportBucket(String v) { this.exportBucket = v; }

    public boolean isExportEnabled() { return exportEnabled; }
    public void setExportEnabled(boolean v) { this.exportEnabled = v; }

    public double getDfEliteThreshold() { return dfEliteThreshold; }
    public void setDfEliteThreshold(double v) { this.dfEliteThreshold = v; }
    public double getDfHighThreshold() { return dfHighThreshold; }
    public void setDfHighThreshold(double v) { this.dfHighThreshold = v; }
    public double getDfMediumThreshold() { return dfMediumThreshold; }
    public void setDfMediumThreshold(double v) { this.dfMediumThreshold = v; }
    public double getLtEliteMs() { return ltEliteMs; }
    public void setLtEliteMs(double v) { this.ltEliteMs = v; }
    public double getLtHighMs() { return ltHighMs; }
    public void setLtHighMs(double v) { this.ltHighMs = v; }
    public double getLtMediumMs() { return ltMediumMs; }
    public void setLtMediumMs(double v) { this.ltMediumMs = v; }
    public double getMttrEliteMs() { return mttrEliteMs; }
    public void setMttrEliteMs(double v) { this.mttrEliteMs = v; }
    public double getMttrHighMs() { return mttrHighMs; }
    public void setMttrHighMs(double v) { this.mttrHighMs = v; }
    public double getMttrMediumMs() { return mttrMediumMs; }
    public void setMttrMediumMs(double v) { this.mttrMediumMs = v; }
    public double getCfrElitePercent() { return cfrElitePercent; }
    public void setCfrElitePercent(double v) { this.cfrElitePercent = v; }
    public double getCfrHighPercent() { return cfrHighPercent; }
    public void setCfrHighPercent(double v) { this.cfrHighPercent = v; }
    public double getCfrMediumPercent() { return cfrMediumPercent; }
    public void setCfrMediumPercent(double v) { this.cfrMediumPercent = v; }
}
