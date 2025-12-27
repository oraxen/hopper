package md.thomas.hopper;

import md.thomas.hopper.coordination.HopperCoordinator;
import md.thomas.hopper.coordination.Lockfile;
import md.thomas.hopper.coordination.Registry;
import md.thomas.hopper.sources.*;
import md.thomas.hopper.version.Version;
import md.thomas.hopper.version.VersionConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Hopper - Plugin Dependency Loader
 * <p>
 * Downloads plugin dependencies from Hangar, Modrinth, Spiget, GitHub, and direct URLs.
 * Multiple plugins can shade Hopper and share dependencies via filesystem coordination.
 * <p>
 * <h2>Usage (Static Registration)</h2>
 * <pre>{@code
 * // In plugin constructor
 * public MyPlugin() {
 *     Hopper.register(this, deps -> {
 *         deps.require(Dependency.hangar("ProtocolLib").minVersion("5.0.0").build());
 *     });
 * }
 * 
 * // In onLoad()
 * public void onLoad() {
 *     DownloadResult result = Hopper.download(this);
 *     if (result.requiresRestart()) {
 *         getLogger().severe("Dependencies downloaded. Restart required!");
 *     }
 * }
 * }</pre>
 */
public final class Hopper {
    
    private static final Map<String, PluginRegistration> registrations = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> downloadedPlugins = new ConcurrentHashMap<>();
    
    private final Path pluginsFolder;
    private final Path coordinationDir;
    private final Logger logger;
    private final Map<Dependency.SourceType, DependencySource> sources;
    
    private Hopper(Builder builder) {
        this.pluginsFolder = builder.pluginsFolder;
        this.coordinationDir = builder.pluginsFolder.resolve(".hopper");
        this.logger = builder.logger != null ? builder.logger : Logger.NOOP;
        this.sources = createSources();
    }
    
    private Map<Dependency.SourceType, DependencySource> createSources() {
        Map<Dependency.SourceType, DependencySource> map = new EnumMap<>(Dependency.SourceType.class);
        map.put(Dependency.SourceType.HANGAR, new HangarSource());
        map.put(Dependency.SourceType.MODRINTH, new ModrinthSource());
        map.put(Dependency.SourceType.SPIGET, new SpigetSource());
        map.put(Dependency.SourceType.GITHUB, new GitHubSource());
        map.put(Dependency.SourceType.URL, new UrlSource());
        return map;
    }
    
    // ========== Static Registration API ==========
    
    /**
     * Register dependencies for a plugin.
     * Call this in your plugin constructor or static initializer.
     *
     * @param pluginName the plugin name
     * @param collector consumer that registers dependencies
     */
    public static void register(@NotNull String pluginName, @NotNull Consumer<DependencyCollector> collector) {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(collector, "collector");
        
        DependencyCollector deps = new DependencyCollector();
        collector.accept(deps);
        
        registrations.put(pluginName, new PluginRegistration(pluginName, deps.getDependencies()));
    }
    
    /**
     * Register dependencies for a Bukkit plugin.
     * Call this in your plugin constructor.
     *
     * @param plugin the plugin instance (must have getName() method)
     * @param collector consumer that registers dependencies
     */
    public static void register(@NotNull Object plugin, @NotNull Consumer<DependencyCollector> collector) {
        String name = getPluginName(plugin);
        register(name, collector);
    }
    
    /**
     * Download all registered dependencies for a plugin.
     * Call this in your plugin's onLoad() method.
     *
     * @param pluginName the plugin name
     * @return download result
     */
    public static DownloadResult download(@NotNull String pluginName) {
        return download(pluginName, null);
    }
    
    /**
     * Download all registered dependencies for a plugin.
     * Call this in your plugin's onLoad() method.
     *
     * @param plugin the plugin instance
     * @return download result
     */
    public static DownloadResult download(@NotNull Object plugin) {
        String name = getPluginName(plugin);
        Path pluginsFolder = getPluginsFolder(plugin);
        return download(name, pluginsFolder);
    }
    
    /**
     * Download all registered dependencies for a plugin with explicit plugins folder.
     *
     * @param pluginName the plugin name
     * @param pluginsFolder the plugins folder (null to auto-detect)
     * @return download result
     */
    public static DownloadResult download(@NotNull String pluginName, @Nullable Path pluginsFolder) {
        return download(pluginName, pluginsFolder, null);
    }

    /**
     * Download all registered dependencies for a plugin with explicit plugins folder and logger.
     *
     * @param pluginName the plugin name
     * @param pluginsFolder the plugins folder (null to auto-detect)
     * @param logger the logger to use (null for NOOP)
     * @return download result
     */
    public static DownloadResult download(@NotNull String pluginName, @Nullable Path pluginsFolder, @Nullable Logger logger) {
        Logger log = logger != null ? logger : Logger.NOOP;

        PluginRegistration registration = registrations.get(pluginName);
        if (registration == null) {
            // No dependencies registered - this can happen if:
            // 1. register() was never called
            // 2. Plugin name doesn't match (typo, different casing)
            // 3. Different classloader (shouldn't happen within same plugin)
            log.warn("No dependencies registered for plugin: " + pluginName);
            if (!registrations.isEmpty()) {
                log.warn("Registered plugins: " + registrations.keySet());
            }
            return new DownloadResult.Builder().build();
        }

        log.info("Processing " + registration.dependencies().size() + " dependency(ies) for " + pluginName);

        // Auto-detect plugins folder if not provided
        if (pluginsFolder == null) {
            pluginsFolder = Path.of("plugins");
        }

        Hopper hopper = builder()
            .pluginsFolder(pluginsFolder)
            .logger(logger)
            .build();

        return hopper.downloadDependencies(pluginName, registration.dependencies());
    }
    
    /**
     * Check if all dependencies for a plugin are ready (downloaded and loaded).
     *
     * @param pluginName the plugin name
     * @return true if all dependencies are ready
     */
    public static boolean isReady(@NotNull String pluginName) {
        return Boolean.TRUE.equals(downloadedPlugins.get(pluginName));
    }
    
    /**
     * Check if all dependencies for a plugin are ready.
     *
     * @param plugin the plugin instance
     * @return true if all dependencies are ready
     */
    public static boolean isReady(@NotNull Object plugin) {
        return isReady(getPluginName(plugin));
    }
    
    // ========== Builder API ==========
    
    /**
     * Create a new Hopper builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ========== Instance Methods ==========
    
    /**
     * Download dependencies with coordination.
     */
    private DownloadResult downloadDependencies(String pluginName, List<Dependency> dependencies) {
        DownloadResult.Builder result = new DownloadResult.Builder();
        
        try {
            // Ensure coordination directory exists
            Files.createDirectories(coordinationDir);
            
            // Use file-based coordination
            try (HopperCoordinator coordinator = HopperCoordinator.acquire(coordinationDir)) {
                // Load or create registry
                Registry registry = coordinator.loadRegistry();
                
                // Register this plugin's dependencies
                registry.registerPlugin(pluginName, dependencies);
                
                // Load lockfile
                Lockfile lockfile = coordinator.loadLockfile();
                
                // Process each dependency
                for (Dependency dep : dependencies) {
                    try {
                        processDependency(dep, registry, lockfile, result);
                    } catch (DependencyException e) {
                        handleFailure(dep, e.getMessage(), result);
                    } catch (Exception e) {
                        handleFailure(dep, e.getMessage(), result);
                    }
                }
                
                // Save updated registry and lockfile
                coordinator.saveRegistry(registry);
                coordinator.saveLockfile(lockfile);
            }
            
            // Mark plugin as ready if successful
            if (result.build().isSuccess()) {
                downloadedPlugins.put(pluginName, true);
            }
            
        } catch (Exception e) {
            logger.error("Failed to process dependencies: " + e.getMessage());
        }
        
        return result.build();
    }
    
    private void processDependency(Dependency dep, Registry registry, Lockfile lockfile, 
                                   DownloadResult.Builder result) {
        String depName = dep.name();
        logger.info("Processing dependency: " + depName);
        
        // Get merged constraint from registry (combines all plugins' constraints)
        VersionConstraint mergedConstraint = registry.getMergedConstraint(depName);
        if (mergedConstraint == null) {
            mergedConstraint = dep.constraint();
        }
        if (mergedConstraint == null) {
            mergedConstraint = VersionConstraint.latest();
        }
        
        // Check lockfile first
        Lockfile.Entry lockedEntry = lockfile.getEntry(depName);
        if (lockedEntry != null) {
            // Check if locked version still satisfies constraint
            Version lockedVersion = Version.tryParse(lockedEntry.resolvedVersion());
            if (lockedVersion != null && mergedConstraint.isSatisfiedBy(lockedVersion)) {
                // Check if file exists
                Path lockedPath = pluginsFolder.resolve(lockedEntry.fileName());
                if (Files.exists(lockedPath)) {
                    logger.info("  Using locked version: " + lockedVersion);
                    result.addExisting(depName, lockedVersion, lockedPath);
                    return;
                }
            }
        }
        
        // Need to resolve version
        DependencySource source = sources.get(dep.sourceType());
        if (source == null) {
            throw new DependencyException(depName, "Unknown source type: " + dep.sourceType());
        }
        
        // Fetch available versions
        logger.info("  Fetching versions from " + dep.sourceType() + "...");
        List<Version> versions = source.fetchVersions(dep);
        if (versions.isEmpty()) {
            throw new DependencyException(depName, "No versions found");
        }
        
        // Select best version
        Version selected = mergedConstraint.selectBest(versions);
        if (selected == null) {
            // Constraint couldn't be satisfied
            if (dep.failurePolicy() == FailurePolicy.WARN_USE_LATEST) {
                logger.warn("  Constraint not satisfied, using latest: " + versions.get(0));
                selected = versions.get(0);
            } else {
                throw new DependencyException(depName, 
                    "No version satisfies constraint: " + mergedConstraint);
            }
        }
        
        logger.info("  Selected version: " + selected);
        
        // Check if already downloaded
        DependencySource.ResolvedDependency resolved = source.resolve(dep, selected);
        Path targetPath = pluginsFolder.resolve(resolved.fileName());
        
        if (Files.exists(targetPath)) {
            // Verify checksum if provided
            if (resolved.sha256() != null) {
                String actualChecksum = md.thomas.hopper.util.Checksum.sha256(targetPath);
                if (resolved.sha256().equalsIgnoreCase(actualChecksum)) {
                    logger.info("  Already downloaded with matching checksum");
                    result.addExisting(depName, selected, targetPath);
                    lockfile.updateEntry(depName, resolved);
                    return;
                } else {
                    logger.warn("  Checksum mismatch, re-downloading");
                    try {
                        Files.deleteIfExists(targetPath);
                    } catch (IOException e) {
                        throw new DependencyException(depName, "Failed to delete file: " + e.getMessage(), e);
                    }
                }
            } else {
                logger.info("  Already downloaded");
                result.addExisting(depName, selected, targetPath);
                lockfile.updateEntry(depName, resolved);
                return;
            }
        }
        
        // Download
        logger.info("  Downloading from: " + resolved.downloadUrl());
        md.thomas.hopper.util.HttpClient.download(resolved.downloadUrl(), targetPath);
        
        // Verify checksum
        if (resolved.sha256() != null) {
            String actualChecksum = md.thomas.hopper.util.Checksum.sha256(targetPath);
            if (!resolved.sha256().equalsIgnoreCase(actualChecksum)) {
                try {
                    Files.deleteIfExists(targetPath);
                } catch (IOException ignored) {
                    // Best effort cleanup
                }
                throw new DependencyException(depName, "Checksum verification failed");
            }
        }
        
        logger.info("  Downloaded successfully: " + resolved.fileName());
        result.addDownloaded(depName, selected, targetPath);
        lockfile.updateEntry(depName, resolved);
    }
    
    private void handleFailure(Dependency dep, String error, DownloadResult.Builder result) {
        String depName = dep.name();
        FailurePolicy policy = dep.failurePolicy();
        
        switch (policy) {
            case FAIL:
                result.addFailed(depName, error, policy);
                logger.error("  FAILED: " + error);
                break;
            case WARN_USE_LATEST:
                result.addFailed(depName, error, policy);
                logger.warn("  WARNING: " + error);
                break;
            case WARN_SKIP:
                result.addSkipped(depName, error);
                logger.warn("  SKIPPED: " + error);
                break;
        }
    }
    
    // ========== Utility Methods ==========
    
    private static String getPluginName(Object plugin) {
        try {
            return (String) plugin.getClass().getMethod("getName").invoke(plugin);
        } catch (Exception e) {
            return plugin.getClass().getSimpleName();
        }
    }
    
    private static Path getPluginsFolder(Object plugin) {
        try {
            Object dataFolder = plugin.getClass().getMethod("getDataFolder").invoke(plugin);
            return ((java.io.File) dataFolder).toPath().getParent();
        } catch (Exception e) {
            return Path.of("plugins");
        }
    }
    
    // ========== Inner Classes ==========
    
    /**
     * Logger interface for Hopper messages.
     */
    @FunctionalInterface
    public interface Logger {
        void log(Level level, String message);
        
        default void info(String message) { log(Level.INFO, message); }
        default void warn(String message) { log(Level.WARN, message); }
        default void error(String message) { log(Level.ERROR, message); }
        
        enum Level { INFO, WARN, ERROR }
        
        Logger NOOP = (level, message) -> {};
        Logger CONSOLE = (level, message) -> System.out.println("[Hopper/" + level + "] " + message);
    }
    
    private record PluginRegistration(String name, List<Dependency> dependencies) {}
    
    /**
     * Builder for Hopper instances.
     */
    public static final class Builder {
        private Path pluginsFolder = Path.of("plugins");
        private Logger logger;
        
        private Builder() {}
        
        /**
         * Set the plugins folder path.
         */
        public Builder pluginsFolder(Path path) {
            this.pluginsFolder = Objects.requireNonNull(path);
            return this;
        }
        
        /**
         * Set the plugins folder path.
         */
        public Builder pluginsFolder(java.io.File file) {
            return pluginsFolder(file.toPath());
        }
        
        /**
         * Set the logger for Hopper messages.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }
        
        /**
         * Build the Hopper instance.
         */
        public Hopper build() {
            return new Hopper(this);
        }
    }
}
