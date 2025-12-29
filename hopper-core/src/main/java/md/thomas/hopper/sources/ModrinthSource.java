package md.thomas.hopper.sources;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyException;
import md.thomas.hopper.DependencySource;
import md.thomas.hopper.DependencySource.ChecksumType;
import md.thomas.hopper.MinecraftVersion;
import md.thomas.hopper.Platform;
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
        // Use effective MC version (explicit > global > null)
        String mcVersion = MinecraftVersion.getEffective(dependency);
        Platform platform = dependency.platform().resolve();

        try {
            // Build URL with query parameters
            StringBuilder url = new StringBuilder(API_BASE)
                .append("/project/").append(slug).append("/version");

            // Add loader filter based on platform
            String[] loaders = platform.modrinthLoaders();
            StringBuilder loadersJson = new StringBuilder("[");
            for (int i = 0; i < loaders.length; i++) {
                if (i > 0) loadersJson.append(",");
                loadersJson.append("\"").append(loaders[i]).append("\"");
            }
            loadersJson.append("]");
            url.append("?loaders=").append(URLEncoder.encode(loadersJson.toString(), StandardCharsets.UTF_8));

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
        Platform platform = dependency.platform().resolve();

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

            // Get files list
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) matchingVersion.get("files");
            if (files == null || files.isEmpty()) {
                throw new DependencyException(slug, "No files found for version " + version);
            }

            // Select the best file based on platform
            Map<String, Object> selectedFile = selectFileForPlatform(files, platform);

            String downloadUrl = (String) selectedFile.get("url");
            String fileName = (String) selectedFile.get("filename");

            @SuppressWarnings("unchecked")
            Map<String, String> hashes = (Map<String, String>) selectedFile.get("hashes");
            String sha512 = hashes != null ? hashes.get("sha512") : null;

            if (downloadUrl == null || fileName == null) {
                throw new DependencyException(slug, "Invalid file info for version " + version);
            }

            return new ResolvedDependency(
                dependency.name(),
                version,
                downloadUrl,
                sha512,
                sha512 != null ? ChecksumType.SHA512 : null,
                dependency.fileName() != null ? dependency.fileName() : fileName
            );
        } catch (DependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new DependencyException(slug, "Failed to resolve Modrinth version: " + e.getMessage(), e);
        }
    }

    /**
     * Select the best file from a list based on the target platform.
     * <p>
     * For multi-file versions (e.g., CommandAPI with Paper and Spigot variants),
     * this selects the file matching the platform. Falls back to primary file
     * or first file if no platform-specific match is found.
     *
     * @param files list of file objects from Modrinth API
     * @param platform the target platform
     * @return the best matching file
     */
    private static Map<String, Object> selectFileForPlatform(
            List<Map<String, Object>> files, Platform platform) {

        if (files.size() == 1) {
            return files.get(0);
        }

        // Platform-specific filename patterns (case-insensitive)
        String[] preferredPatterns = getPlatformFilePatterns(platform);

        // First pass: look for platform-specific file
        for (String pattern : preferredPatterns) {
            for (Map<String, Object> file : files) {
                String filename = (String) file.get("filename");
                if (filename != null && filename.toLowerCase().contains(pattern.toLowerCase())) {
                    return file;
                }
            }
        }

        // Second pass: avoid files for incompatible platforms
        String[] avoidPatterns = getIncompatibleFilePatterns(platform);
        for (Map<String, Object> file : files) {
            String filename = (String) file.get("filename");
            if (filename == null) continue;

            boolean shouldAvoid = false;
            for (String pattern : avoidPatterns) {
                if (filename.toLowerCase().contains(pattern.toLowerCase())) {
                    shouldAvoid = true;
                    break;
                }
            }
            if (!shouldAvoid) {
                return file;
            }
        }

        // Fall back to primary file or first file
        for (Map<String, Object> file : files) {
            Boolean primary = (Boolean) file.get("primary");
            if (Boolean.TRUE.equals(primary)) {
                return file;
            }
        }
        return files.get(0);
    }

    /**
     * Get filename patterns to prefer for this platform.
     */
    private static String[] getPlatformFilePatterns(Platform platform) {
        return switch (platform) {
            case FOLIA -> new String[]{"folia", "paper"};
            case PURPUR -> new String[]{"purpur", "paper"};
            case PAPER -> new String[]{"paper"};
            case SPIGOT -> new String[]{"spigot"};
            case BUKKIT -> new String[]{"bukkit", "spigot"};
            case VELOCITY -> new String[]{"velocity"};
            case BUNGEECORD -> new String[]{"bungeecord", "bungee"};
            case WATERFALL -> new String[]{"waterfall", "bungeecord", "bungee"};
            case AUTO -> new String[]{}; // No preference
        };
    }

    /**
     * Get filename patterns to avoid for this platform.
     */
    private static String[] getIncompatibleFilePatterns(Platform platform) {
        return switch (platform) {
            case SPIGOT, BUKKIT -> new String[]{"paper", "folia", "purpur", "velocity", "bungee"};
            case PAPER -> new String[]{"folia", "velocity", "bungee"};
            case FOLIA -> new String[]{"velocity", "bungee"};
            case PURPUR -> new String[]{"folia", "velocity", "bungee"};
            case VELOCITY -> new String[]{"paper", "spigot", "bukkit", "folia", "bungee"};
            case BUNGEECORD, WATERFALL -> new String[]{"paper", "spigot", "bukkit", "folia", "velocity"};
            case AUTO -> new String[]{};
        };
    }
}
