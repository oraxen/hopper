package md.thomas.hopper;

import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for plugin sources (Hangar, Modrinth, Spiget, GitHub, etc.).
 */
public interface DependencySource {
    
    /**
     * @return the source type identifier (e.g., "hangar", "modrinth", "github")
     */
    @NotNull String type();
    
    /**
     * Fetch available versions from this source.
     *
     * @param dependency the dependency to fetch versions for
     * @return list of available versions, newest first
     * @throws DependencyException if fetching fails
     */
    @NotNull List<Version> fetchVersions(@NotNull Dependency dependency) throws DependencyException;
    
    /**
     * Resolve the download information for a specific version.
     *
     * @param dependency the dependency
     * @param version the version to resolve
     * @return resolved download info
     * @throws DependencyException if resolution fails
     */
    @NotNull ResolvedDependency resolve(@NotNull Dependency dependency, @NotNull Version version) throws DependencyException;
    
    /**
     * Resolved dependency with download URL and metadata.
     */
    record ResolvedDependency(
        @NotNull String name,
        @NotNull Version version,
        @NotNull String downloadUrl,
        @Nullable String checksum,
        @Nullable ChecksumType checksumType,
        @NotNull String fileName
    ) {
        /**
         * Convenience constructor for no checksum.
         */
        public ResolvedDependency(
            @NotNull String name,
            @NotNull Version version,
            @NotNull String downloadUrl,
            @NotNull String fileName
        ) {
            this(name, version, downloadUrl, null, null, fileName);
        }
    }

    /**
     * Supported checksum algorithms.
     */
    enum ChecksumType {
        SHA256("SHA-256"),
        SHA512("SHA-512"),
        SHA1("SHA-1"),
        MD5("MD5");

        private final String algorithm;

        ChecksumType(String algorithm) {
            this.algorithm = algorithm;
        }

        /**
         * @return the Java Security algorithm name
         */
        public String algorithm() {
            return algorithm;
        }
    }
}
