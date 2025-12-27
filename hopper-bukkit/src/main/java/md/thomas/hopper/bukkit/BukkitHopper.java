package md.thomas.hopper.bukkit;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyCollector;
import md.thomas.hopper.DownloadResult;
import md.thomas.hopper.Hopper;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit/Spigot convenience wrapper for Hopper.
 * <p>
 * <h2>Usage</h2>
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
 */
public final class BukkitHopper {
    
    private BukkitHopper() {}
    
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
