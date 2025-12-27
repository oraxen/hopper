package md.thomas.hopper.bukkit;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyCollector;
import md.thomas.hopper.DownloadResult;
import md.thomas.hopper.Hopper;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit/Spigot convenience wrapper for Hopper.
 * <p>
 * <h2>Basic Usage (Download Only)</h2>
 * <pre>{@code
 * public class MyPlugin extends JavaPlugin {
 *
 *     public MyPlugin() {
 *         // Register in constructor
 *         BukkitHopper.register(this, deps -> {
 *             deps.require(Dependency.hangar("ProtocolLib").minVersion("5.0.0").build());
 *         });
 *     }
 *
 *     @Override
 *     public void onLoad() {
 *         // Download in onLoad (before onEnable)
 *         DownloadResult result = BukkitHopper.download(this);
 *
 *         if (result.requiresRestart()) {
 *             getLogger().severe("Dependencies downloaded. Restart required!");
 *         }
 *     }
 *
 *     @Override
 *     public void onEnable() {
 *         if (!BukkitHopper.isReady(this)) {
 *             getLogger().severe("Dependencies not loaded. Disabling.");
 *             getServer().getPluginManager().disablePlugin(this);
 *             return;
 *         }
 *         // Safe to use dependencies
 *     }
 * }
 * }</pre>
 * <p>
 * <h2>Auto-Load Usage (No Restart Required)</h2>
 * <pre>{@code
 * @Override
 * public void onLoad() {
 *     // Download AND automatically load plugins without restart
 *     DownloadAndLoadResult result = BukkitHopper.downloadAndLoad(this);
 *
 *     if (!result.downloadResult().isSuccess()) {
 *         getLogger().severe("Some dependencies failed to download!");
 *     }
 *
 *     if (!result.loadResult().isSuccess()) {
 *         getLogger().warning("Some plugins failed to auto-load, restart may be needed.");
 *     }
 * }
 * }</pre>
 */
public final class BukkitHopper {

    private BukkitHopper() {}

    /**
     * Result of a combined download and load operation.
     */
    public record DownloadAndLoadResult(
        @NotNull DownloadResult downloadResult,
        @NotNull PluginLoader.LoadResult loadResult
    ) {
        /**
         * @return true if all downloads succeeded and all plugins were loaded
         */
        public boolean isFullySuccessful() {
            return downloadResult.isSuccess() && loadResult.isSuccess();
        }

        /**
         * @return true if no restart is required (all downloaded plugins were loaded)
         */
        public boolean noRestartRequired() {
            return !downloadResult.requiresRestart() || loadResult.hasLoaded();
        }
    }
    
    /**
     * Register dependencies for a plugin.
     * Call this in your plugin constructor.
     *
     * @param plugin the plugin instance
     * @param collector consumer that registers dependencies
     */
    public static void register(@NotNull Plugin plugin, @NotNull Consumer<DependencyCollector> collector) {
        Hopper.register(plugin.getName(), collector);
    }
    
    /**
     * Download all registered dependencies.
     * Call this in your plugin's onLoad() method.
     *
     * @param plugin the plugin instance
     * @return download result
     */
    @NotNull
    public static DownloadResult download(@NotNull Plugin plugin) {
        Path pluginsFolder = plugin.getDataFolder().getParentFile().toPath();
        
        // Create logger adapter
        Hopper.Logger logger = createLogger(plugin.getLogger());
        
        // Build Hopper with Bukkit settings
        Hopper hopper = Hopper.builder()
            .pluginsFolder(pluginsFolder)
            .logger(logger)
            .build();
        
        return Hopper.download(plugin.getName(), pluginsFolder);
    }

    /**
     * Download all registered dependencies and automatically load them.
     * <p>
     * This method downloads dependencies like {@link #download(Plugin)}, but then
     * attempts to load any newly downloaded plugins at runtime without requiring
     * a server restart.
     * <p>
     * <b>Note:</b> While most plugins can be hot-loaded successfully, some plugins
     * with complex initialization or class dependencies may still require a restart.
     *
     * @param plugin the plugin instance
     * @return combined download and load result
     */
    @NotNull
    public static DownloadAndLoadResult downloadAndLoad(@NotNull Plugin plugin) {
        Logger logger = plugin.getLogger();

        // First, download all dependencies
        DownloadResult downloadResult = download(plugin);

        // If nothing was downloaded, return early with empty load result
        if (!downloadResult.requiresRestart()) {
            return new DownloadAndLoadResult(
                downloadResult,
                new PluginLoader.LoadResult(List.of(), List.of())
            );
        }

        // Collect paths of newly downloaded plugins
        List<Path> pluginsToLoad = new ArrayList<>();
        for (DownloadResult.DownloadedDependency dep : downloadResult.downloaded()) {
            pluginsToLoad.add(dep.path());
        }

        // Log what we're about to do
        logger.info("[Hopper] Auto-loading " + pluginsToLoad.size() + " downloaded plugin(s)...");

        // Load the downloaded plugins
        PluginLoader.LoadResult loadResult = PluginLoader.loadAll(pluginsToLoad, logger);

        // Log the final result
        logDownloadAndLoadResult(plugin, downloadResult, loadResult);

        return new DownloadAndLoadResult(downloadResult, loadResult);
    }

    /**
     * Log combined download and load results.
     *
     * @param plugin the plugin instance
     * @param downloadResult the download result
     * @param loadResult the load result
     */
    public static void logDownloadAndLoadResult(
            @NotNull Plugin plugin,
            @NotNull DownloadResult downloadResult,
            @NotNull PluginLoader.LoadResult loadResult) {

        Logger logger = plugin.getLogger();

        if (loadResult.hasLoaded()) {
            logger.info("========================================");
            logger.info("  HOPPER - Plugins Auto-Loaded");
            logger.info("========================================");

            for (PluginLoader.LoadedPlugin loaded : loadResult.loaded()) {
                logger.info("  ✓ " + loaded.name() + " v" + loaded.version());
            }

            if (!loadResult.failed().isEmpty()) {
                logger.warning("");
                logger.warning("  Failed to auto-load:");
                for (PluginLoader.FailedPlugin failed : loadResult.failed()) {
                    logger.warning("  ✗ " + failed.path().getFileName() + ": " + failed.error());
                }
                logger.warning("");
                logger.warning("  These may require a server RESTART.");
            }

            logger.info("========================================");
        } else if (!downloadResult.downloaded().isEmpty()) {
            // Downloaded but none could be loaded
            logger.severe("========================================");
            logger.severe("  HOPPER - Auto-Load Failed");
            logger.severe("========================================");
            logger.severe("  Downloaded plugins could not be auto-loaded.");
            logger.severe("  Please RESTART the server.");
            logger.severe("========================================");
        }

        // Also log any download failures
        if (!downloadResult.failed().isEmpty()) {
            logger.severe("[Hopper] Failed to download:");
            for (DownloadResult.FailedDependency dep : downloadResult.failed()) {
                logger.severe("  - " + dep.name() + ": " + dep.error());
            }
        }
    }

    /**
     * Check if all dependencies are ready.
     *
     * @param plugin the plugin instance
     * @return true if all dependencies are satisfied
     */
    public static boolean isReady(@NotNull Plugin plugin) {
        return Hopper.isReady(plugin.getName());
    }
    
    /**
     * Create a Hopper instance for manual use.
     *
     * @param plugin the plugin instance
     * @return Hopper builder
     */
    @NotNull
    public static Hopper.Builder builder(@NotNull Plugin plugin) {
        return Hopper.builder()
            .pluginsFolder(plugin.getDataFolder().getParentFile())
            .logger(createLogger(plugin.getLogger()));
    }
    
    /**
     * Log download results to the plugin logger.
     *
     * @param plugin the plugin instance
     * @param result the download result
     */
    public static void logResult(@NotNull Plugin plugin, @NotNull DownloadResult result) {
        Logger logger = plugin.getLogger();
        
        if (result.requiresRestart()) {
            logger.severe("========================================");
            logger.severe("  HOPPER - Dependencies Downloaded");
            logger.severe("========================================");
            
            for (DownloadResult.DownloadedDependency dep : result.downloaded()) {
                logger.severe("  + " + dep.name() + " v" + dep.version());
            }
            
            logger.severe("");
            logger.severe("  Please RESTART the server to load them.");
            logger.severe("========================================");
        } else if (!result.existing().isEmpty()) {
            logger.info("[Hopper] All dependencies satisfied:");
            for (DownloadResult.ExistingDependency dep : result.existing()) {
                logger.info("  - " + dep.name() + " v" + dep.version());
            }
        }
        
        if (!result.skipped().isEmpty()) {
            logger.warning("[Hopper] Skipped optional dependencies:");
            for (DownloadResult.SkippedDependency dep : result.skipped()) {
                logger.warning("  - " + dep.name() + ": " + dep.reason());
            }
        }
        
        if (!result.failed().isEmpty()) {
            logger.severe("[Hopper] Failed dependencies:");
            for (DownloadResult.FailedDependency dep : result.failed()) {
                logger.severe("  - " + dep.name() + ": " + dep.error());
            }
        }
    }
    
    private static Hopper.Logger createLogger(Logger bukkitLogger) {
        return (level, message) -> {
            switch (level) {
                case INFO:
                    bukkitLogger.info(message);
                    break;
                case WARN:
                    bukkitLogger.warning(message);
                    break;
                case ERROR:
                    bukkitLogger.severe(message);
                    break;
            }
        };
    }
}
