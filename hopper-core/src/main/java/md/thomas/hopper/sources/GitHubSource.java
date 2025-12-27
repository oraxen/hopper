package md.thomas.hopper.sources;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyException;
import md.thomas.hopper.DependencySource;
import md.thomas.hopper.util.FileUtils;
import md.thomas.hopper.util.HttpClient;
import md.thomas.hopper.util.JsonParser;
import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dependency source for GitHub Releases.
 * <p>
 * API: https://api.github.com
 */
public final class GitHubSource implements DependencySource {
    
    private static final String API_BASE = "https://api.github.com";
    
    @Override
    public @NotNull String type() {
        return "github";
    }
    
    @Override
    public @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException {
        String repo = dependency.identifier();
        
        try {
            String url = API_BASE + "/repos/" + repo + "/releases?per_page=25";
            String response = HttpClient.get(url, "Accept", "application/vnd.github.v3+json");
            List<Object> releases = JsonParser.parseArray(response);
            
            if (releases == null || releases.isEmpty()) {
                throw new DependencyException(repo, "No releases found on GitHub");
            }
            
            List<Version> versions = new ArrayList<>();
            for (Object item : releases) {
                @SuppressWarnings("unchecked")
                Map<String, Object> release = (Map<String, Object>) item;
                
                // Skip drafts and prereleases unless explicitly wanted
                Boolean draft = (Boolean) release.get("draft");
                if (Boolean.TRUE.equals(draft)) {
                    continue;
                }
                
                String tagName = (String) release.get("tag_name");
                if (tagName != null) {
                    // Remove common prefixes like "v" or "release-"
                    String cleanVersion = tagName.replaceFirst("^[vV]", "")
                                                  .replaceFirst("^release[-_]?", "");
                    Version v = Version.tryParse(cleanVersion);
                    if (v == null) {
                        v = Version.tryParse(tagName);
                    }
                    if (v != null) {
                        versions.add(v);
                    }
                }
            }
            
            return versions;
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(repo, "Failed to fetch releases from GitHub: " + e.getMessage(), e);
        }
    }
    
    @Override
    public @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) 
            throws DependencyException {
        String repo = dependency.identifier();
        String assetPattern = dependency.assetPattern();
        
        try {
            // Find the release with this version
            String releasesUrl = API_BASE + "/repos/" + repo + "/releases?per_page=50";
            String response = HttpClient.get(releasesUrl, "Accept", "application/vnd.github.v3+json");
            List<Object> releases = JsonParser.parseArray(response);
            
            if (releases == null) {
                throw new DependencyException(repo, "Failed to parse GitHub releases");
            }
            
            Map<String, Object> targetRelease = null;
            for (Object item : releases) {
                @SuppressWarnings("unchecked")
                Map<String, Object> release = (Map<String, Object>) item;
                String tagName = (String) release.get("tag_name");
                
                if (tagName != null) {
                    String cleanVersion = tagName.replaceFirst("^[vV]", "")
                                                  .replaceFirst("^release[-_]?", "");
                    if (cleanVersion.equals(version.raw()) || tagName.equals(version.raw())) {
                        targetRelease = release;
                        break;
                    }
                }
            }
            
            if (targetRelease == null) {
                throw new DependencyException(repo, "Release not found on GitHub: " + version);
            }
            
            // Find matching asset
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assets = (List<Map<String, Object>>) targetRelease.get("assets");
            if (assets == null || assets.isEmpty()) {
                throw new DependencyException(repo, "No assets found for release " + version);
            }
            
            Map<String, Object> targetAsset = null;
            
            if (assetPattern != null) {
                // Find asset matching pattern
                for (Map<String, Object> asset : assets) {
                    String name = (String) asset.get("name");
                    if (name != null && FileUtils.matches(name, assetPattern)) {
                        targetAsset = asset;
                        break;
                    }
                }
            }
            
            if (targetAsset == null) {
                // Default: find first .jar file
                for (Map<String, Object> asset : assets) {
                    String name = (String) asset.get("name");
                    if (name != null && name.endsWith(".jar")) {
                        targetAsset = asset;
                        break;
                    }
                }
            }
            
            if (targetAsset == null) {
                // Fall back to first asset
                targetAsset = assets.get(0);
            }
            
            String downloadUrl = (String) targetAsset.get("browser_download_url");
            String fileName = (String) targetAsset.get("name");
            
            if (downloadUrl == null) {
                throw new DependencyException(repo, "No download URL for asset");
            }
            
            return new ResolvedDependency(
                dependency.name(),
                version,
                downloadUrl,
                null, // GitHub doesn't provide checksums in API
                dependency.fileName() != null ? dependency.fileName() : fileName
            );
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(repo, "Failed to resolve GitHub release: " + e.getMessage(), e);
        }
    }
}
