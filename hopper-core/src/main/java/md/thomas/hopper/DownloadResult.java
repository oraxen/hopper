package md.thomas.hopper;

import md.thomas.hopper.version.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a Hopper download operation.
 */
public final class DownloadResult {
    
    private final List<DownloadedDependency> downloaded;
    private final List<ExistingDependency> existing;
    private final List<SkippedDependency> skipped;
    private final List<FailedDependency> failed;
    
    DownloadResult(List<DownloadedDependency> downloaded,
                   List<ExistingDependency> existing,
                   List<SkippedDependency> skipped,
                   List<FailedDependency> failed) {
        this.downloaded = Collections.unmodifiableList(downloaded);
        this.existing = Collections.unmodifiableList(existing);
        this.skipped = Collections.unmodifiableList(skipped);
        this.failed = Collections.unmodifiableList(failed);
    }
    
    /**
     * @return true if new dependencies were downloaded (requires server restart)
     */
    public boolean requiresRestart() {
        return !downloaded.isEmpty();
    }
    
    /**
     * @return true if all required dependencies are satisfied (existing or downloaded)
     */
    public boolean isSuccess() {
        return failed.isEmpty();
    }
    
    /**
     * @return list of newly downloaded dependencies
     */
    @NotNull
    public List<DownloadedDependency> downloaded() {
        return downloaded;
    }
    
    /**
     * @return list of dependencies that were already present
     */
    @NotNull
    public List<ExistingDependency> existing() {
        return existing;
    }
    
    /**
     * @return list of optional dependencies that were skipped
     */
    @NotNull
    public List<SkippedDependency> skipped() {
        return skipped;
    }
    
    /**
     * @return list of dependencies that failed to resolve/download
     */
    @NotNull
    public List<FailedDependency> failed() {
        return failed;
    }
    
    /**
     * A dependency that was newly downloaded.
     */
    public record DownloadedDependency(
        @NotNull String name,
        @NotNull Version version,
        @NotNull Path path
    ) {}
    
    /**
     * A dependency that was already present on disk.
     */
    public record ExistingDependency(
        @NotNull String name,
        @NotNull Version version,
        @NotNull Path path
    ) {}
    
    /**
     * A dependency that was skipped (optional with WARN_SKIP policy).
     */
    public record SkippedDependency(
        @NotNull String name,
        @NotNull String reason
    ) {}
    
    /**
     * A dependency that failed to resolve or download.
     */
    public record FailedDependency(
        @NotNull String name,
        @NotNull String error,
        @NotNull FailurePolicy policy
    ) {}
    
    // Builder for constructing results
    static class Builder {
        private final List<DownloadedDependency> downloaded = new java.util.ArrayList<>();
        private final List<ExistingDependency> existing = new java.util.ArrayList<>();
        private final List<SkippedDependency> skipped = new java.util.ArrayList<>();
        private final List<FailedDependency> failed = new java.util.ArrayList<>();
        
        Builder addDownloaded(String name, Version version, Path path) {
            downloaded.add(new DownloadedDependency(name, version, path));
            return this;
        }
        
        Builder addExisting(String name, Version version, Path path) {
            existing.add(new ExistingDependency(name, version, path));
            return this;
        }
        
        Builder addSkipped(String name, String reason) {
            skipped.add(new SkippedDependency(name, reason));
            return this;
        }
        
        Builder addFailed(String name, String error, FailurePolicy policy) {
            failed.add(new FailedDependency(name, error, policy));
            return this;
        }
        
        DownloadResult build() {
            return new DownloadResult(downloaded, existing, skipped, failed);
        }
    }
}
