package io.jenkins.plugins.dorametrics.export;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * HTTP endpoint export configuration.
 * Sends JSON snapshots to any HTTP endpoint via POST.
 */
public class HttpExportConfig extends ExportStorageConfig {

    private String url;
    private String credentialsId;

    @DataBoundConstructor
    public HttpExportConfig() {}

    @Override
    public String getStorageType() { return "HTTP"; }

    public String getUrl() { return url; }

    @DataBoundSetter
    public void setUrl(String url) { this.url = url; }

    @Override
    public String getCredentialsId() { return credentialsId; }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) { this.credentialsId = credentialsId; }

    @Extension
    public static class DescriptorImpl extends Descriptor<ExportStorageConfig> {
        @Override
        public String getDisplayName() {
            return "HTTP Endpoint";
        }
    }
}
