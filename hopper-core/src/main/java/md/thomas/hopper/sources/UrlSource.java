package md.thomas.hopper.sources;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyException;
import md.thomas.hopper.DependencySource;
import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;

/**
 * Dependency source for direct URL downloads.
 * <p>
 * This source doesn't support version resolution - it just downloads from the provided URL.
 */
public final class UrlSource implements DependencySource {
    
    @Override
    public @NotNull String type() {
        return "url";
    }
    
    @Override
    public @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException {
        // URL source doesn't support version listing
        // Return the constraint version if specified, or a dummy version
        if (dependency.constraint() != null && dependency.constraint().getExact() != null) {
            return List.of(dependency.constraint().getExact());
        }
        
        // Return a dummy version for latest
        return List.of(Version.parse("1.0.0"));
    }
    
    @Override
    public @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) 
            throws DependencyException {
        String url = dependency.identifier();
        
        try {
            // Extract filename from URL
            String fileName = dependency.fileName();
            if (fileName == null) {
                URI uri = URI.create(url);
                String path = uri.getPath();
                if (path != null && !path.isEmpty()) {
                    int lastSlash = path.lastIndexOf('/');
                    fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                    
                    // Clean up query strings
                    int queryStart = fileName.indexOf('?');
                    if (queryStart >= 0) {
                        fileName = fileName.substring(0, queryStart);
                    }
                }
                
                if (fileName == null || fileName.isEmpty()) {
                    fileName = "download-" + System.currentTimeMillis() + ".jar";
                }
            }
            
            return new ResolvedDependency(
                dependency.name(),
                version,
                url,
                dependency.sha256(),
                fileName
            );
        } catch (Exception e) {
            throw new DependencyException(url, "Failed to resolve URL: " + e.getMessage(), e);
        }
    }
}
