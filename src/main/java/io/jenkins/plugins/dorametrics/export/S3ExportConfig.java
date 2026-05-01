package io.jenkins.plugins.dorametrics.export;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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
    }
}
