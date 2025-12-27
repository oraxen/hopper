package md.thomas.hopper.paper;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.DependencyCollector;
import md.thomas.hopper.DownloadResult;
import md.thomas.hopper.Hopper;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Paper PluginLoader integration for early dependency loading.
 * <p>
 * Paper 1.19.4+ supports a custom PluginLoader that runs before the plugin class is loaded.
 * This allows dependencies to be downloaded and available when onEnable() runs.
 * <p>
 * <h2>Usage</h2>
 * <p>
 * 1. Create a bootstrap class that implements Paper's PluginLoader:
 * <pre>{@code
 * public class MyPluginBootstrap implements PluginLoader {
 *     @Override
 *     public void classloader(PluginClasspathBuilder builder) {
 *         DownloadResult result = HopperBootstrap.create(builder.getContext())
 *             .require(Dependency.hangar("ProtocolLib").minVersion("5.0.0").build())
 *             .require(Dependency.modrinth("packetevents").latest().build())
 *             .download();
 *         
 *         if (result.requiresRestart()) {
 *             throw new RuntimeException("Dependencies downloaded. Restart required!");
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * 2. Reference it in paper-plugin.yml:
 * <pre>{@code
 * name: MyPlugin
 * version: 1.0.0
 * main: com.example.MyPlugin
 * loader: com.example.MyPluginBootstrap
 * api-version: "1.19"
 * }</pre>
 */
public final class HopperBootstrap {
    
    private final Path pluginsFolder;
    private final String pluginName;
    private final Logger logger;
    private final List<Dependency> dependencies = new ArrayList<>();
    
    private HopperBootstrap(Path pluginsFolder, String pluginName, Logger logger) {
        this.pluginsFolder = pluginsFolder;
        this.pluginName = pluginName;
        this.logger = logger;
    }
    
    /**
     * Create a bootstrap hopper from a Paper PluginProviderContext.
     * <p>
     * Call this in your PluginLoader.classloader() method.
     *
     * @param context the PluginProviderContext from Paper
     * @return the bootstrap builder
     */
    @NotNull
    public static HopperBootstrap create(@NotNull Object context) {
        try {
            // Use reflection to access Paper's PluginProviderContext
            Class<?> contextClass = context.getClass();
            
            // Get plugin meta
            Object pluginMeta = contextClass.getMethod("getConfiguration").invoke(context);
            String pluginName = (String) pluginMeta.getClass().getMethod("getName").invoke(pluginMeta);
            
            // Get data directory and derive plugins folder
            Path dataDirectory = (Path) contextClass.getMethod("getDataDirectory").invoke(context);
            Path pluginsFolder = dataDirectory.getParent();
            
            // Get logger
            Object loggerObj = contextClass.getMethod("getLogger").invoke(context);
            Logger logger = loggerObj instanceof Logger ? (Logger) loggerObj : Logger.getLogger(pluginName);
            
            return new HopperBootstrap(pluginsFolder, pluginName, logger);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HopperBootstrap from context", e);
        }
    }
    
    /**
     * Create a bootstrap hopper with explicit parameters.
     *
     * @param pluginsFolder the server's plugins folder
     * @param pluginName the plugin name
     * @param logger the logger to use
     * @return the bootstrap builder
     */
    @NotNull
    public static HopperBootstrap create(@NotNull Path pluginsFolder, @NotNull String pluginName, @NotNull Logger logger) {
        return new HopperBootstrap(pluginsFolder, pluginName, logger);
    }
    
    /**
     * Add a required dependency.
     *
     * @param dependency the dependency to require
     * @return this builder for chaining
     */
    @NotNull
    public HopperBootstrap require(@NotNull Dependency dependency) {
        dependencies.add(dependency);
        return this;
    }
    
    /**
     * Add multiple dependencies.
     *
     * @param deps the dependencies to add
     * @return this builder for chaining
     */
    @NotNull
    public HopperBootstrap require(@NotNull Dependency... deps) {
        for (Dependency dep : deps) {
            dependencies.add(dep);
        }
        return this;
    }
    
    /**
     * Add dependencies using a collector.
     *
     * @param collector consumer that registers dependencies
     * @return this builder for chaining
     */
    @NotNull
    public HopperBootstrap require(@NotNull Consumer<DependencyCollector> collector) {
        DependencyCollector deps = new DependencyCollector();
        collector.accept(deps);
        dependencies.addAll(deps.getDependencies());
        return this;
    }
    
    /**
     * Download all registered dependencies.
     *
     * @return the download result
     */
    @NotNull
    public DownloadResult download() {
        // Register dependencies with static Hopper
        Hopper.register(pluginName, deps -> {
            for (Dependency dep : dependencies) {
                deps.require(dep);
            }
        });
        
        // Download using Hopper
        DownloadResult result = Hopper.download(pluginName, pluginsFolder);
        
        // Log results
        logResult(result);
        
        return result;
    }
    
    private void logResult(DownloadResult result) {
        if (result.requiresRestart()) {
            logger.severe("========================================");
            logger.severe("  HOPPER BOOTSTRAP - Dependencies Downloaded");
            logger.severe("========================================");
            
            for (DownloadResult.DownloadedDependency dep : result.downloaded()) {
                logger.severe("  + " + dep.name() + " v" + dep.version());
            }
            
            logger.severe("");
            logger.severe("  Server must be RESTARTED to load them.");
            logger.severe("========================================");
        } else if (!result.existing().isEmpty()) {
            logger.info("[Hopper] Dependencies satisfied:");
            for (DownloadResult.ExistingDependency dep : result.existing()) {
                logger.info("  - " + dep.name() + " v" + dep.version());
            }
        }
        
        if (!result.skipped().isEmpty()) {
            logger.warning("[Hopper] Skipped optional:");
            for (DownloadResult.SkippedDependency dep : result.skipped()) {
                logger.warning("  - " + dep.name() + ": " + dep.reason());
            }
        }
    }
}
