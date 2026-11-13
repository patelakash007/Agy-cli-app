package com.agy.runtime;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Install record persisted next to the installed CLI (B06).
 *
 * The format is a strict bounded key/value document rather than free-form JSON
 * so it can be parsed and validated offline. Unknown keys, duplicates, missing
 * keys, oversized input and malformed values are rejected; a record is never
 * interpreted when it cannot be fully verified.
 */
public final class CliInstallMetadata {

    public static final int SCHEMA = 1;
    public static final int MAX_BYTES = 4096;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public final int schema;
    public final String version;
    public final String artifactSha512;
    public final String sourceUrl;
    public final String binarySha256;
    public final long installedAtEpochSeconds;

    public CliInstallMetadata(int schema, String version, String artifactSha512,
                              String sourceUrl, String binarySha256,
                              long installedAtEpochSeconds) {
        this.schema = schema;
        this.version = version;
        this.artifactSha512 = artifactSha512;
        this.sourceUrl = sourceUrl;
        this.binarySha256 = binarySha256;
        this.installedAtEpochSeconds = installedAtEpochSeconds;
    }

    public String serialize() {
        return "schema=" + schema
                + "\nversion=" + version
                + "\nsha512=" + artifactSha512
                + "\nsource=" + sourceUrl
                + "\nbinary_sha256=" + binarySha256
                + "\ninstalled_at=" + installedAtEpochSeconds
                + "\n";
    }

    /** @return a fully validated record, or null for any malformed input. */
    public static CliInstallMetadata parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.getBytes(UTF_8).length > MAX_BYTES) {
            return null;
        }
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                return null;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c == 0x7F) {
                    return null;
                }
            }
            if (values.put(key, value) != null) {
                return null;
            }
        }
        if (values.size() != 6
                || !values.containsKey("schema")
                || !values.containsKey("version")
                || !values.containsKey("sha512")
                || !values.containsKey("source")
                || !values.containsKey("binary_sha256")
                || !values.containsKey("installed_at")) {
            return null;
        }
        int schema;
        long installedAt;
        try {
            schema = Integer.parseInt(values.get("schema"));
            installedAt = Long.parseLong(values.get("installed_at"));
        } catch (NumberFormatException e) {
            return null;
        }
        if (schema != SCHEMA || installedAt <= 0) {
            return null;
        }
        String version = values.get("version");
        String artifactSha512 = CliInstallPolicy.normalizeSha512(values.get("sha512"));
        String sourceUrl = values.get("source");
        String binarySha256 = CliInstallPolicy.normalizeSha256(values.get("binary_sha256"));
        if (!CliInstallPolicy.isValidVersion(version)
                || artifactSha512 == null
                || binarySha256 == null
                || !CliInstallPolicy.isValidArtifactUrl(sourceUrl)) {
            return null;
        }
        return new CliInstallMetadata(schema, version, artifactSha512, sourceUrl,
                binarySha256, installedAt);
    }
}
