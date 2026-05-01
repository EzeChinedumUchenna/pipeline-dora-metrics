package io.jenkins.plugins.dorametrics.export;

import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.io.IOException;

/**
 * Base class for export storage configurations.
 * Each storage type extends this with its own fields and upload logic.
 * Third-party plugins can add new export types by extending this class.
 */
public abstract class ExportStorageConfig implements Describable<ExportStorageConfig> {

    public abstract String getStorageType();

    public abstract String getCredentialsId();

    /**
     * Upload data to the configured storage backend.
     * Each implementation handles its own credential resolution and upload protocol.
     */
    public abstract void upload(String data, String fileName) throws IOException, InterruptedException;

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<ExportStorageConfig> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }
}
