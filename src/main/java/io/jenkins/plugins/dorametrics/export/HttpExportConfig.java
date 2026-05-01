package io.jenkins.plugins.dorametrics.export;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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

        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            Jenkins.get(),
                            StandardUsernamePasswordCredentials.class,
                            java.util.Collections.emptyList(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }
    }
}
