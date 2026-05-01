package io.jenkins.plugins.dorametrics.export;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * Base class for export storage configurations.
 * Each storage type (S3, HTTP) extends this with its own fields.
 */
public abstract class ExportStorageConfig implements Describable<ExportStorageConfig> {

    public abstract String getStorageType();

    public abstract String getCredentialsId();

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<ExportStorageConfig> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }
}
