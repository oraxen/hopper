package md.thomas.hopper;

import md.thomas.hopper.version.UpdatePolicy;
import md.thomas.hopper.version.Version;
import md.thomas.hopper.version.VersionConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a plugin dependency from a specific source.
 * <p>
 * Use the static factory methods to create dependencies:
 * <ul>
 *   <li>{@link #hangar(String)} - Hangar (PaperMC repository)</li>
 *   <li>{@link #modrinth(String)} - Modrinth</li>
 *   <li>{@link #spiget(int)} - SpigotMC (via Spiget API)</li>
 *   <li>{@link #github(String)} - GitHub Releases</li>
 *   <li>{@link #url(String)} - Direct URL</li>
 * </ul>
 */
public final class Dependency {

    private final SourceType sourceType;
    private final String identifier;
    private final @Nullable String name;
    private final @Nullable String fileName;
    private final @Nullable String sha256;
    private final @Nullable VersionConstraint constraint;
    private final UpdatePolicy updatePolicy;
    private final FailurePolicy failurePolicy;
    private final @Nullable String minecraftVersion;
    private final @Nullable String assetPattern;
    private final Platform platform;

    private Dependency(Builder<?> builder) {
        this.sourceType = builder.sourceType;
        this.identifier = builder.identifier;
        this.name = builder.name;
        this.fileName = builder.fileName;
        this.sha256 = builder.sha256;
        this.constraint = builder.constraint;
        this.updatePolicy = builder.updatePolicy;
        this.failurePolicy = builder.failurePolicy;
        this.minecraftVersion = builder.minecraftVersion;
        this.assetPattern = builder.assetPattern;
        this.platform = builder.platform;
    }
    
    // ========== Factory Methods ==========
    
    /**
     * Create a dependency from Hangar (PaperMC's plugin repository).
     *
     * @param slug the project slug (e.g., "ProtocolLib")
     */
    public static HangarBuilder hangar(String slug) {
        return new HangarBuilder(slug);
    }
    
    /**
     * Create a dependency from Modrinth.
     *
     * @param slugOrId the project slug or ID (e.g., "packetevents")
     */
    public static ModrinthBuilder modrinth(String slugOrId) {
        return new ModrinthBuilder(slugOrId);
    }
    
    /**
     * Create a dependency from SpigotMC (via Spiget API).
     *
     * @param resourceId the SpigotMC resource ID
     */
    public static SpigetBuilder spiget(int resourceId) {
        return new SpigetBuilder(resourceId);
    }
    
    /**
     * Create a dependency from GitHub Releases.
     *
     * @param repo the repository in "owner/repo" format
     */
    public static GitHubBuilder github(String repo) {
        return new GitHubBuilder(repo);
    }
    
    /**
     * Create a dependency from a direct URL.
     *
     * @param downloadUrl the direct download URL
     */
    public static UrlBuilder url(String downloadUrl) {
        return new UrlBuilder(downloadUrl);
    }
    
    // ========== Getters ==========
    
    public SourceType sourceType() {
        return sourceType;
    }
    
    public String identifier() {
        return identifier;
    }
    
    /**
     * @return display name, or identifier if not set
     */
    public String name() {
        return name != null ? name : identifier;
    }
    
    public @Nullable String fileName() {
        return fileName;
    }
    
    public @Nullable String sha256() {
        return sha256;
    }
    
    public @Nullable VersionConstraint constraint() {
        return constraint;
    }
    
    public UpdatePolicy updatePolicy() {
        return updatePolicy;
    }
    
    public FailurePolicy failurePolicy() {
        return failurePolicy;
    }
    
    public @Nullable String minecraftVersion() {
        return minecraftVersion;
    }
    
    public @Nullable String assetPattern() {
        return assetPattern;
    }

    /**
     * @return the platform preference (AUTO by default)
     */
    public Platform platform() {
        return platform;
    }

    /**
     * @return true if this is a latest version request (no constraint or constraint is latest)
     */
    public boolean isLatest() {
        return constraint == null || constraint.isLatest();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Dependency)) return false;
        Dependency other = (Dependency) obj;
        return sourceType == other.sourceType && Objects.equals(identifier, other.identifier);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sourceType, identifier);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceType.name().toLowerCase()).append(":").append(identifier);
        if (constraint != null) {
            sb.append("@").append(constraint);
        }
        return sb.toString();
    }
    
    // ========== Source Types ==========
    
    public enum SourceType {
        HANGAR,
        MODRINTH,
        SPIGET,
        GITHUB,
        URL
    }
    
    // ========== Builder Base ==========
    
    public static abstract class Builder<T extends Builder<T>> {
        final SourceType sourceType;
        final String identifier;
        @Nullable String name;
        @Nullable String fileName;
        @Nullable String sha256;
        @Nullable VersionConstraint constraint;
        UpdatePolicy updatePolicy = UpdatePolicy.MINOR;
        FailurePolicy failurePolicy = FailurePolicy.FAIL;
        @Nullable String minecraftVersion;
        @Nullable String assetPattern;
        Platform platform = Platform.AUTO;

        Builder(SourceType sourceType, String identifier) {
            this.sourceType = sourceType;
            this.identifier = Objects.requireNonNull(identifier, "identifier");
        }
        
        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }
        
        /**
         * Set a display name for this dependency.
         */
        public T name(String name) {
            this.name = name;
            return self();
        }
        
        /**
         * Set a custom filename for the downloaded file.
         */
        public T fileName(String fileName) {
            this.fileName = fileName;
            return self();
        }
        
        /**
         * Set an expected SHA-256 checksum for verification.
         */
        public T sha256(String sha256) {
            this.sha256 = sha256;
            return self();
        }
        
        /**
         * Request an exact version.
         */
        public T version(String version) {
            this.constraint = VersionConstraint.exact(version);
            return self();
        }
        
        /**
         * Request at least this version (inclusive).
         */
        public T minVersion(String minVersion) {
            this.constraint = VersionConstraint.atLeast(minVersion);
            return self();
        }
        
        /**
         * Request a version range.
         * <p>
         * Examples: {@code ">=5.0.0 <6.0.0"}, {@code ">1.0.0"}
         */
        public T versionRange(String range) {
            this.constraint = VersionConstraint.parse(range);
            return self();
        }
        
        /**
         * Request the latest version.
         */
        public T latest() {
            this.constraint = VersionConstraint.latest();
            return self();
        }
        
        /**
         * Set the update policy for this dependency.
         */
        public T updatePolicy(UpdatePolicy policy) {
            this.updatePolicy = Objects.requireNonNull(policy);
            return self();
        }
        
        /**
         * Set what happens if this dependency fails to resolve/download.
         */
        public T onFailure(FailurePolicy policy) {
            this.failurePolicy = Objects.requireNonNull(policy);
            return self();
        }
        
        /**
         * Filter versions by Minecraft version compatibility.
         */
        public T minecraftVersion(String mcVersion) {
            this.minecraftVersion = mcVersion;
            return self();
        }

        /**
         * Set the target platform for this dependency.
         * <p>
         * Defaults to {@link Platform#AUTO} which auto-detects the server type.
         * Use this to override when you need a specific platform variant.
         *
         * @param platform the target platform
         */
        public T platform(Platform platform) {
            this.platform = Objects.requireNonNull(platform);
            return self();
        }

        /**
         * Build the dependency.
         */
        public Dependency build() {
            return new Dependency(this);
        }
    }
    
    // ========== Source-Specific Builders ==========
    
    public static final class HangarBuilder extends Builder<HangarBuilder> {
        HangarBuilder(String slug) {
            super(SourceType.HANGAR, slug);
        }
    }
    
    public static final class ModrinthBuilder extends Builder<ModrinthBuilder> {
        ModrinthBuilder(String slugOrId) {
            super(SourceType.MODRINTH, slugOrId);
        }
    }
    
    public static final class SpigetBuilder extends Builder<SpigetBuilder> {
        SpigetBuilder(int resourceId) {
            super(SourceType.SPIGET, String.valueOf(resourceId));
        }
    }
    
    public static final class GitHubBuilder extends Builder<GitHubBuilder> {
        GitHubBuilder(String repo) {
            super(SourceType.GITHUB, repo);
            if (!repo.contains("/")) {
                throw new IllegalArgumentException("GitHub repo must be in 'owner/repo' format");
            }
        }
        
        /**
         * Set a pattern to match release assets.
         * <p>
         * Examples: {@code "*.jar"}, {@code "*-spigot-*.jar"}, {@code "ProtocolLib.jar"}
         */
        public GitHubBuilder assetPattern(String pattern) {
            this.assetPattern = pattern;
            return self();
        }
    }
    
    public static final class UrlBuilder extends Builder<UrlBuilder> {
        UrlBuilder(String url) {
            super(SourceType.URL, url);
        }
    }
}
