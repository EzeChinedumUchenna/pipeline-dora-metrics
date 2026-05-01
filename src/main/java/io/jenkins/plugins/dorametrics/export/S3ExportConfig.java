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
 * S3-compatible export configuration.
 * Works with AWS S3, Backblaze B2, MinIO, DigitalOcean Spaces.
 */
public class S3ExportConfig extends ExportStorageConfig {

    private String endpoint;
    private String bucket;
    private String credentialsId;

    @DataBoundConstructor
    public S3ExportConfig() {}

    @Override
    public String getStorageType() { return "S3"; }

    public String getEndpoint() { return endpoint; }

    @DataBoundSetter
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getBucket() { return bucket; }

    @DataBoundSetter
    public void setBucket(String bucket) { this.bucket = bucket; }

    @Override
    public String getCredentialsId() { return credentialsId; }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) { this.credentialsId = credentialsId; }

    @Extension
    public static class DescriptorImpl extends Descriptor<ExportStorageConfig> {
        @Override
        public String getDisplayName() {
            return "S3-Compatible (AWS S3, Backblaze B2, MinIO)";
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
