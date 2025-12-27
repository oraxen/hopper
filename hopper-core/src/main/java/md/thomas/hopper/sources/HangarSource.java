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
 * Dependency source for Hangar (PaperMC's plugin repository).
 * <p>
 * API: https://hangar.papermc.io/api/v1
 */
public final class HangarSource implements DependencySource {
    
    private static final String API_BASE = "https://hangar.papermc.io/api/v1";
    
    @Override
    public @NotNull String type() {
        return "hangar";
    }
    
    @Override
    public @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException {
        String slug = dependency.identifier();
        String mcVersion = dependency.minecraftVersion();
        
        try {
            // Build URL with query parameters
            StringBuilder url = new StringBuilder(API_BASE)
                .append("/projects/").append(slug).append("/versions")
                .append("?limit=25&platform=PAPER");
            
            if (mcVersion != null) {
                url.append("&platformVersion=").append(mcVersion);
            }
            
            String response = HttpClient.get(url.toString());
            Map<String, Object> json = JsonParser.parseObject(response);
            
            if (json == null) {
                throw new DependencyException(slug, "Failed to parse Hangar response");
            }
            
            List<Object> result = JsonParser.getList(json, "result");
            if (result == null || result.isEmpty()) {
                throw new DependencyException(slug, "No versions found on Hangar");
            }
            
            List<Version> versions = new ArrayList<>();
            for (Object item : result) {
                @SuppressWarnings("unchecked")
                Map<String, Object> versionData = (Map<String, Object>) item;
                String name = (String) versionData.get("name");
                if (name != null) {
                    Version v = Version.tryParse(name);
                    if (v != null) {
                        versions.add(v);
                    }
                }
            }
            
            return versions;
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(slug, "Failed to fetch versions from Hangar: " + e.getMessage(), e);
        }
    }
    
    @Override
    public @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) 
            throws DependencyException {
        String slug = dependency.identifier();
        
        try {
            // Fetch version details
            String url = API_BASE + "/projects/" + slug + "/versions/" + version.raw();
            String response = HttpClient.get(url);
            Map<String, Object> json = JsonParser.parseObject(response);
            
            if (json == null) {
                throw new DependencyException(slug, "Failed to parse Hangar version response");
            }
            
            // Get download info
            @SuppressWarnings("unchecked")
            Map<String, Object> downloads = (Map<String, Object>) json.get("downloads");
            if (downloads == null || downloads.isEmpty()) {
                throw new DependencyException(slug, "No downloads found for version " + version);
            }
            
            // Prefer PAPER platform
            @SuppressWarnings("unchecked")
            Map<String, Object> paperDownload = (Map<String, Object>) downloads.get("PAPER");
            if (paperDownload == null) {
                // Fall back to first available
                paperDownload = (Map<String, Object>) downloads.values().iterator().next();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> fileInfo = (Map<String, Object>) paperDownload.get("fileInfo");
            String fileName = fileInfo != null ? (String) fileInfo.get("name") : null;
            String sha256 = fileInfo != null ? (String) fileInfo.get("sha256Hash") : null;
            
            // Build download URL
            String downloadUrl = (String) paperDownload.get("downloadUrl");
            if (downloadUrl == null) {
                downloadUrl = API_BASE + "/projects/" + slug + "/versions/" + version.raw() + "/PAPER/download";
            } else if (!downloadUrl.startsWith("http")) {
                downloadUrl = "https://hangar.papermc.io" + downloadUrl;
            }
            
            if (fileName == null) {
                fileName = slug + "-" + version.raw() + ".jar";
            }
            
            return new ResolvedDependency(
                dependency.name(),
                version,
                downloadUrl,
                sha256,
                dependency.fileName() != null ? dependency.fileName() : fileName
            );
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(slug, "Failed to resolve Hangar version: " + e.getMessage(), e);
        }
    }
}
