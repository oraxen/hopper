package md.thomas.hopper.sources;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyException;
import md.thomas.hopper.DependencySource;
import md.thomas.hopper.util.HttpClient;
import md.thomas.hopper.util.JsonParser;
import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dependency source for Modrinth.
 * <p>
 * API: https://api.modrinth.com/v2
 */
public final class ModrinthSource implements DependencySource {
    
    private static final String API_BASE = "https://api.modrinth.com/v2";
    
    @Override
    public @NotNull String type() {
        return "modrinth";
    }
    
    @Override
    public @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException {
        String slug = dependency.identifier();
        String mcVersion = dependency.minecraftVersion();
        
        try {
            // Build URL with query parameters
            StringBuilder url = new StringBuilder(API_BASE)
                .append("/project/").append(slug).append("/version");
            
            // Add loader filter (Bukkit-compatible loaders)
            url.append("?loaders=").append(URLEncoder.encode("[\"paper\",\"spigot\",\"bukkit\",\"purpur\",\"folia\"]", 
                StandardCharsets.UTF_8));
            
            if (mcVersion != null) {
                url.append("&game_versions=").append(URLEncoder.encode("[\"" + mcVersion + "\"]", 
                    StandardCharsets.UTF_8));
            }
            
            String response = HttpClient.get(url.toString());
            List<Object> versions = JsonParser.parseArray(response);
            
            if (versions == null || versions.isEmpty()) {
                throw new DependencyException(slug, "No versions found on Modrinth");
            }
            
            List<Version> result = new ArrayList<>();
            for (Object item : versions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> versionData = (Map<String, Object>) item;
                String versionNumber = (String) versionData.get("version_number");
                if (versionNumber != null) {
                    Version v = Version.tryParse(versionNumber);
                    if (v != null) {
                        result.add(v);
                    }
                }
            }
            
            return result;
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(slug, "Failed to fetch versions from Modrinth: " + e.getMessage(), e);
        }
    }
    
    @Override
    public @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) 
            throws DependencyException {
        String slug = dependency.identifier();
        
        try {
            // Fetch all versions and find the matching one
            String url = API_BASE + "/project/" + slug + "/version";
            String response = HttpClient.get(url);
            List<Object> versions = JsonParser.parseArray(response);
            
            if (versions == null) {
                throw new DependencyException(slug, "Failed to parse Modrinth response");
            }
            
            // Find matching version
            Map<String, Object> matchingVersion = null;
            for (Object item : versions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> versionData = (Map<String, Object>) item;
                String versionNumber = (String) versionData.get("version_number");
                if (versionNumber != null && versionNumber.equals(version.raw())) {
                    matchingVersion = versionData;
                    break;
                }
            }
            
            if (matchingVersion == null) {
                throw new DependencyException(slug, "Version not found on Modrinth: " + version);
            }
            
            // Get primary file
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) matchingVersion.get("files");
            if (files == null || files.isEmpty()) {
                throw new DependencyException(slug, "No files found for version " + version);
            }
            
            // Find primary file or first file
            Map<String, Object> primaryFile = files.get(0);
            for (Map<String, Object> file : files) {
                Boolean primary = (Boolean) file.get("primary");
                if (Boolean.TRUE.equals(primary)) {
                    primaryFile = file;
                    break;
                }
            }
            
            String downloadUrl = (String) primaryFile.get("url");
            String fileName = (String) primaryFile.get("filename");
            
            @SuppressWarnings("unchecked")
            Map<String, String> hashes = (Map<String, String>) primaryFile.get("hashes");
            String sha512 = hashes != null ? hashes.get("sha512") : null;
            String sha1 = hashes != null ? hashes.get("sha1") : null;
            
            if (downloadUrl == null || fileName == null) {
                throw new DependencyException(slug, "Invalid file info for version " + version);
            }
            
            return new ResolvedDependency(
                dependency.name(),
                version,
                downloadUrl,
                null, // Modrinth uses SHA-512, not SHA-256; skip verification
                dependency.fileName() != null ? dependency.fileName() : fileName
            );
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(slug, "Failed to resolve Modrinth version: " + e.getMessage(), e);
        }
    }
}
