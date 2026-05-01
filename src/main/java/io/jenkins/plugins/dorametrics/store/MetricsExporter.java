package io.jenkins.plugins.dorametrics.store;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.security.ACL;
import io.jenkins.plugins.dorametrics.DoraGlobalConfiguration;
import io.jenkins.plugins.dorametrics.store.MetricsStore.BuildRecord;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exports daily metrics snapshots to S3-compatible object storage.
 * Uses AWS Signature V4 for authentication (works with AWS S3, Backblaze B2, MinIO).
 * No AWS SDK dependency - uses Java's built-in HttpClient.
 */
public class MetricsExporter {

    private static final Logger LOGGER = Logger.getLogger(MetricsExporter.class.getName());
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Export a daily snapshot of metrics to the configured storage backend.
     */
    public static void exportDailySnapshot() {
        DoraGlobalConfiguration config = DoraGlobalConfiguration.get();
        if (config == null || !config.isExportEnabled()) return;

        try {
            MetricsStore store = MetricsStore.getInstance();
            long now = System.currentTimeMillis();
            long dayAgo = now - 86400_000;

            List<BuildRecord> builds = store.getAllBuilds(dayAgo, now);
            if (builds.isEmpty()) {
                LOGGER.fine("No builds in last 24h, skipping export");
                return;
            }

            String jsonData = buildSnapshot(builds, store, now);
            String fileName = String.format("dora-metrics/%tF/snapshot.json", new Date(now));

            io.jenkins.plugins.dorametrics.export.ExportStorageConfig storageConfig = config.getExportStorage();
            if (storageConfig == null) {
                LOGGER.warning("Export enabled but no storage configured");
                return;
            }

            String credentialsId = storageConfig.getCredentialsId();
            String accessKey = "";
            String secretKey = "";
            if (credentialsId != null && !credentialsId.isEmpty()) {
                StandardUsernamePasswordCredentials creds = CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM2,
                        java.util.Collections.emptyList())
                        .stream()
                        .filter(c -> credentialsId.equals(c.getId()))
                        .findFirst()
                        .orElse(null);
                if (creds != null) {
                    accessKey = creds.getUsername();
                    secretKey = creds.getPassword().getPlainText();
                } else {
                    LOGGER.warning("Export credentials not found: " + credentialsId);
                    return;
                }
            }

            if (storageConfig instanceof io.jenkins.plugins.dorametrics.export.HttpExportConfig httpConfig) {
                String authHeader = resolveHttpAuth(credentialsId);
                uploadHttp(httpConfig.getUrl(), authHeader, jsonData);
            } else if (storageConfig instanceof io.jenkins.plugins.dorametrics.export.S3ExportConfig s3Config) {
                String endpoint = s3Config.getEndpoint();
                String region = extractRegion(endpoint);
                uploadS3Compatible(endpoint, s3Config.getBucket(), fileName, jsonData, accessKey, secretKey, region);
            }

            LOGGER.info("Exported daily snapshot: " + builds.size() + " builds to " + storageConfig.getStorageType());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to export metrics snapshot", e);
        }
    }

    /**
     * Export a full dump for testing/debugging.
     */
    public static String exportFullDump(int days) {
        MetricsStore store = MetricsStore.getInstance();
        long now = System.currentTimeMillis();
        long fromMs = now - ((long) days * 86400_000);
        List<BuildRecord> builds = store.getAllBuilds(fromMs, now);
        return buildSnapshot(builds, store, now);
    }

    private static String buildSnapshot(List<BuildRecord> builds, MetricsStore store, long timestamp) {
        JSONObject snapshot = new JSONObject();
        snapshot.put("exported_at", timestamp);
        snapshot.put("total_builds", builds.size());

        JSONArray buildsArr = new JSONArray();
        for (BuildRecord b : builds) {
            JSONObject bj = new JSONObject();
            bj.put("job", b.jobName);
            bj.put("build", b.buildNumber);
            bj.put("timestamp", b.timestamp);
            bj.put("duration_ms", b.durationMs);
            bj.put("result", b.result);
            bj.put("trigger", b.triggerType);
            bj.put("branch", b.branch);

            JSONArray stagesArr = new JSONArray();
            for (MetricsStore.StageRecord s : store.getStages(b.id)) {
                JSONObject sj = new JSONObject();
                sj.put("name", s.stageName);
                sj.put("duration_ms", s.durationMs);
                sj.put("result", s.result);
                stagesArr.add(sj);
            }
            bj.put("stages", stagesArr);
            buildsArr.add(bj);
        }
        snapshot.put("builds", buildsArr);
        return snapshot.toString(2);
    }

    /**
     * Upload to S3-compatible storage using AWS Signature V4.
     * Works with AWS S3, Backblaze B2, MinIO, DigitalOcean Spaces.
     */
    public static void uploadS3Compatible(String endpoint, String bucket, String key,
                                           String data, String accessKey, String secretKey,
                                           String region) throws IOException, InterruptedException {
        byte[] payload = data.getBytes(StandardCharsets.UTF_8);
        String payloadHash = sha256Hex(payload);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String amzDate = dateFormat.format(new Date());
        String dateStamp = amzDate.substring(0, 8);

        String host = endpoint.replaceAll("https?://", "");
        String url = endpoint + "/" + bucket + "/" + key;

        // Canonical request
        String canonicalUri = "/" + bucket + "/" + key;
        String canonicalQueryString = "";
        String canonicalHeaders = "host:" + host + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = "PUT\n" + canonicalUri + "\n" + canonicalQueryString + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

        // String to sign
        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = algorithm + "\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        // Signing key
        byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, "s3");
        String signature = hmacSha256Hex(signingKey, stringToSign);

        String authorization = algorithm + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOGGER.fine("S3 upload success: " + key);
        } else {
            LOGGER.warning("S3 upload failed: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Resolve HTTP auth header from credentials.
     * StringCredentials → Bearer token, UsernamePassword → Basic auth.
     */
    private static String resolveHttpAuth(String credentialsId) {
        if (credentialsId == null || credentialsId.isEmpty()) return null;

        // Try StringCredentials first (secret text → Bearer token)
        StringCredentials secretCreds = CredentialsProvider.lookupCredentialsInItemGroup(
                        StringCredentials.class, Jenkins.get(), ACL.SYSTEM2,
                        java.util.Collections.emptyList())
                .stream()
                .filter(c -> credentialsId.equals(c.getId()))
                .findFirst().orElse(null);
        if (secretCreds != null) {
            return "Bearer " + secretCreds.getSecret().getPlainText();
        }

        // Try UsernamePassword (→ Basic auth)
        StandardUsernamePasswordCredentials basicCreds = CredentialsProvider.lookupCredentialsInItemGroup(
                        StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM2,
                        java.util.Collections.emptyList())
                .stream()
                .filter(c -> credentialsId.equals(c.getId()))
                .findFirst().orElse(null);
        if (basicCreds != null) {
            String raw = basicCreds.getUsername() + ":" + basicCreds.getPassword().getPlainText();
            return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        LOGGER.warning("HTTP export credentials not found: " + credentialsId);
        return null;
    }

    /**
     * Upload to generic HTTP endpoint.
     */
    private static void uploadHttp(String endpoint, String authHeader, String data) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .header("Content-Type", "application/json");

        if (authHeader != null && !authHeader.isEmpty()) {
            builder.header("Authorization", authHeader);
        }

        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOGGER.fine("HTTP export success");
        } else {
            LOGGER.warning("HTTP export failed: HTTP " + response.statusCode());
        }
    }

    private static String extractRegion(String endpoint) {
        // s3.us-east-005.backblazeb2.com → us-east-005
        // s3.us-west-2.amazonaws.com → us-west-2
        if (endpoint == null || endpoint.isEmpty()) return "us-east-1";
        String host = endpoint.replaceAll("https?://", "");
        String[] parts = host.split("\\.");
        if (parts.length >= 3 && parts[0].equals("s3")) {
            return parts[1];
        }
        return "us-east-1";
    }

    // AWS SigV4 helper methods
    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return bytesToHex(hmacSha256(key, data));
    }

    private static byte[] getSignatureKey(String secret, String dateStamp, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
