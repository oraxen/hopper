package md.thomas.hopper.sources;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyException;
import md.thomas.hopper.DependencySource;
import md.thomas.hopper.util.HttpClient;
import md.thomas.hopper.util.JsonParser;
import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dependency source for SpigotMC via Spiget API.
 * <p>
 * API: https://api.spiget.org/v2
 * <p>
 * Note: Some resources may require manual download from SpigotMC (premium resources).
 */
public final class SpigetSource implements DependencySource {
    
    private static final String API_BASE = "https://api.spiget.org/v2";
    
    @Override
    public @NotNull String type() {
        return "spiget";
    }
    
    @Override
    public @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException {
        String resourceId = dependency.identifier();
        
        try {
            // Fetch resource info first to get name
            String resourceUrl = API_BASE + "/resources/" + resourceId;
            String resourceResponse = HttpClient.get(resourceUrl);
            Map<String, Object> resourceJson = JsonParser.parseObject(resourceResponse);
            
            if (resourceJson == null) {
                throw new DependencyException(resourceId, "Resource not found on Spiget");
            }
            
            // Fetch versions
            String versionsUrl = API_BASE + "/resources/" + resourceId + "/versions?size=25&sort=-releaseDate";
            String response = HttpClient.get(versionsUrl);
            List<Object> versions = JsonParser.parseArray(response);
            
            if (versions == null || versions.isEmpty()) {
                // Fallback: try to get current version from resource info
                String currentVersion = (String) resourceJson.get("version");
                if (currentVersion != null) {
                    Version v = Version.tryParse(currentVersion);
                    if (v != null) {
                        return List.of(v);
                    }
                }
                throw new DependencyException(resourceId, "No versions found on Spiget");
            }
            
            List<Version> result = new ArrayList<>();
            for (Object item : versions) {
                @SuppressWarnings("unchecked")
                Map<String, Object> versionData = (Map<String, Object>) item;
                String name = (String) versionData.get("name");
                if (name != null) {
                    Version v = Version.tryParse(name);
                    if (v != null) {
                        result.add(v);
                    }
                }
            }
            
            // If no parseable versions, use current
            if (result.isEmpty()) {
                String currentVersion = (String) resourceJson.get("version");
                if (currentVersion != null) {
                    Version v = Version.tryParse(currentVersion);
                    if (v != null) {
                        result.add(v);
                    }
                }
            }
            
            return result;
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(resourceId, "Failed to fetch versions from Spiget: " + e.getMessage(), e);
        }
    }
    
    @Override
    public @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) 
            throws DependencyException {
        String resourceId = dependency.identifier();
        
        try {
            // Fetch resource info
            String resourceUrl = API_BASE + "/resources/" + resourceId;
            String resourceResponse = HttpClient.get(resourceUrl);
            Map<String, Object> resourceJson = JsonParser.parseObject(resourceResponse);
            
            if (resourceJson == null) {
                throw new DependencyException(resourceId, "Resource not found on Spiget");
            }
            
            String name = (String) resourceJson.get("name");
            if (name == null) {
                name = "Resource-" + resourceId;
            }
            
            // Check if external URL is available
            Boolean external = (Boolean) resourceJson.get("external");
            @SuppressWarnings("unchecked")
            Map<String, Object> file = (Map<String, Object>) resourceJson.get("file");
            String externalUrl = file != null ? (String) file.get("externalUrl") : null;
            
            String downloadUrl;
            if (Boolean.TRUE.equals(external) && externalUrl != null && !externalUrl.isEmpty()) {
                downloadUrl = externalUrl;
            } else {
                // Use Spiget download endpoint
                downloadUrl = API_BASE + "/resources/" + resourceId + "/download";
            }
            
            // Generate filename
            String fileName = dependency.fileName();
            if (fileName == null) {
                String safeName = name.replaceAll("[^a-zA-Z0-9.-]", "_");
                fileName = safeName + "-" + version.raw() + ".jar";
            }
            
            return new ResolvedDependency(
                dependency.name() != null ? dependency.name() : name,
                version,
                downloadUrl,
                fileName
            );
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(resourceId, "Failed to resolve Spiget resource: " + e.getMessage(), e);
        }
    }
}
