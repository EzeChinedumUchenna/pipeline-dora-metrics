package io.jenkins.plugins.dorametrics.export;

import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class ExportConfigTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void s3DescriptorRegistered() {
        Descriptor<?> d = Jenkins.get().getDescriptor(S3ExportConfig.class);
        assertNotNull("S3ExportConfig descriptor should be registered", d);
        assertEquals("S3-Compatible (AWS S3, Backblaze B2, MinIO)", d.getDisplayName());
    }

    @Test
    public void httpDescriptorRegistered() {
        Descriptor<?> d = Jenkins.get().getDescriptor(HttpExportConfig.class);
        assertNotNull("HttpExportConfig descriptor should be registered", d);
        assertEquals("HTTP Endpoint", d.getDisplayName());
    }

    @Test
    public void s3FillCredentialsReturnsNonNull() {
        S3ExportConfig.DescriptorImpl d = (S3ExportConfig.DescriptorImpl)
                Jenkins.get().getDescriptor(S3ExportConfig.class);
        assertNotNull(d);

        ListBoxModel items = d.doFillCredentialsIdItems("");
        assertNotNull("Credentials list should not be null", items);
        // Should have at least the empty value option
        assertTrue("Should include empty value option", items.size() >= 1);
        assertEquals("First option should be empty value", "", items.get(0).value);
    }

    @Test
    public void httpFillCredentialsReturnsNonNull() {
        HttpExportConfig.DescriptorImpl d = (HttpExportConfig.DescriptorImpl)
                Jenkins.get().getDescriptor(HttpExportConfig.class);
        assertNotNull(d);

        ListBoxModel items = d.doFillCredentialsIdItems("");
        assertNotNull("Credentials list should not be null", items);
        assertTrue("Should include empty value option", items.size() >= 1);
        assertEquals("First option should be empty value", "", items.get(0).value);
    }

    @Test
    public void s3ConfigFieldsWork() {
        S3ExportConfig config = new S3ExportConfig();
        config.setEndpoint("https://s3.us-east-005.backblazeb2.com");
        config.setBucket("my-bucket");
        config.setCredentialsId("my-creds");

        assertEquals("https://s3.us-east-005.backblazeb2.com", config.getEndpoint());
        assertEquals("my-bucket", config.getBucket());
        assertEquals("my-creds", config.getCredentialsId());
        assertEquals("S3", config.getStorageType());
    }

    @Test
    public void httpConfigFieldsWork() {
        HttpExportConfig config = new HttpExportConfig();
        config.setUrl("https://example.com/webhook");
        config.setCredentialsId("http-creds");

        assertEquals("https://example.com/webhook", config.getUrl());
        assertEquals("http-creds", config.getCredentialsId());
        assertEquals("HTTP", config.getStorageType());
    }
}
