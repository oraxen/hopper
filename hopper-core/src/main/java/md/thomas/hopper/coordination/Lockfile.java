package md.thomas.hopper.coordination;

import md.thomas.hopper.DependencySource;
import md.thomas.hopper.DependencySource.ChecksumType;
import md.thomas.hopper.util.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records exact resolved versions for reproducibility.
 * <p>
 * Stored in {@code plugins/.hopper/hopper.lock}
 */
public final class Lockfile {
    
    private static final int VERSION = 1;
    
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    
    public Lockfile() {
    }
    
    /**
     * Get the lockfile entry for a dependency.
     *
     * @param dependencyName the dependency name
     * @return the entry, or null if not found
     */
    public @Nullable Entry getEntry(@NotNull String dependencyName) {
        return entries.get(dependencyName);
    }
    
    /**
     * Update or create an entry for a resolved dependency.
     */
    public void updateEntry(@NotNull String dependencyName, @NotNull DependencySource.ResolvedDependency resolved) {
        entries.put(dependencyName, new Entry(
            resolved.name(),
            resolved.version().toString(),
            resolved.checksum(),
            resolved.checksumType(),
            resolved.downloadUrl(),
            resolved.fileName()
        ));
    }
    
    /**
     * Remove an entry.
     */
    public void removeEntry(@NotNull String dependencyName) {
        entries.remove(dependencyName);
    }
    
    /**
     * Parse from JSON.
     */
    public static Lockfile fromJson(String json) {
        Lockfile lockfile = new Lockfile();
        
        Map<String, Object> root = JsonParser.parseObject(json);
        if (root == null) {
            return lockfile;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> deps = (Map<String, Object>) root.get("dependencies");
        if (deps == null) {
            return lockfile;
        }
        
        for (Map.Entry<String, Object> entry : deps.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) entry.getValue();

            String name = (String) data.get("name");
            if (name == null) name = entry.getKey();

            // Parse checksum type (handles both old "sha256" and new "checksumType" formats)
            String checksum = (String) data.get("checksum");
            String checksumTypeStr = (String) data.get("checksumType");
            ChecksumType checksumType = null;

            if (checksumTypeStr != null) {
                try {
                    checksumType = ChecksumType.valueOf(checksumTypeStr);
                } catch (IllegalArgumentException ignored) {
                    // Unknown checksum type, skip verification
                }
            } else if (data.get("sha256") != null) {
                // Backwards compatibility: old lockfiles used "sha256" field
                checksum = (String) data.get("sha256");
                checksumType = ChecksumType.SHA256;
            }

            lockfile.entries.put(entry.getKey(), new Entry(
                name,
                (String) data.get("resolvedVersion"),
                checksum,
                checksumType,
                (String) data.get("downloadUrl"),
                (String) data.get("fileName")
            ));
        }
        
        return lockfile;
    }
    
    /**
     * Serialize to JSON.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"generated\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"dependencies\": {\n");
        
        boolean first = true;
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;

            Entry e = entry.getValue();
            sb.append("    \"").append(JsonParser.escape(entry.getKey())).append("\": {\n");
            sb.append("      \"name\": \"").append(JsonParser.escape(e.name)).append("\",\n");
            sb.append("      \"resolvedVersion\": \"").append(JsonParser.escape(e.resolvedVersion)).append("\",\n");
            if (e.checksum != null && e.checksumType != null) {
                sb.append("      \"checksum\": \"").append(JsonParser.escape(e.checksum)).append("\",\n");
                sb.append("      \"checksumType\": \"").append(e.checksumType.name()).append("\",\n");
            }
            sb.append("      \"downloadUrl\": \"").append(JsonParser.escape(e.downloadUrl)).append("\",\n");
            sb.append("      \"fileName\": \"").append(JsonParser.escape(e.fileName)).append("\"\n");
            sb.append("    }");
        }
        
        sb.append("\n  }\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    /**
     * A lockfile entry for a single dependency.
     */
    public record Entry(
        @NotNull String name,
        @NotNull String resolvedVersion,
        @Nullable String checksum,
        @Nullable ChecksumType checksumType,
        @NotNull String downloadUrl,
        @NotNull String fileName
    ) {}
}
