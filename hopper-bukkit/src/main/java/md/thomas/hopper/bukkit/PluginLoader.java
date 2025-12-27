package md.thomas.hopper.bukkit;

import md.thomas.hopper.coordination.HopperCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for dynamically loading plugins at runtime without server restart.
 * <p>
 * This class uses Bukkit's PluginManager to load plugin JAR files that were
 * downloaded during the current server session. Loaded plugins will have their
 * onLoad() and onEnable() methods called automatically.
 * <p>
 * <b>Limitations:</b>
 * <ul>
 *   <li>Some plugins may not work correctly when hot-loaded</li>
 *   <li>Plugins with complex class dependencies may require restart</li>
 *   <li>Circular dependencies between plugins cannot be resolved at runtime</li>
 * </ul>
 */
public final class PluginLoader {

    private PluginLoader() {}

    /**
     * Result of a plugin load operation.
     */
    public record LoadResult(
        @NotNull List<LoadedPlugin> loaded,
        @NotNull List<FailedPlugin> failed
    ) {
        /**
         * @return true if all plugins were loaded successfully
         */
        public boolean isSuccess() {
            return failed.isEmpty();
        }

        /**
         * @return true if any plugins were loaded
         */
        public boolean hasLoaded() {
            return !loaded.isEmpty();
        }
    }

    /**
     * A plugin that was successfully loaded.
     */
    public record LoadedPlugin(
        @NotNull String name,
        @NotNull String version,
        @NotNull Path path
    ) {}

    /**
     * A plugin that failed to load.
     */
    public record FailedPlugin(
        @NotNull Path path,
        @NotNull String error
    ) {}

    /**
     * A plugin to load, with its expected name and path.
     */
    public record PluginToLoad(
        @NotNull String name,
        @NotNull Path path
    ) {}

    /**
     * Load a single plugin JAR file.
     *
     * @param pluginPath the path to the plugin JAR
     * @return the loaded Plugin instance, or null if loading failed
     */
    @Nullable
    public static Plugin load(@NotNull Path pluginPath) {
        return load(pluginPath, null);
    }

    /**
     * Load a single plugin JAR file with logging.
     *
     * @param pluginPath the path to the plugin JAR
     * @param logger optional logger for status messages
     * @return the loaded Plugin instance, or null if loading failed
     */
    @Nullable
    public static Plugin load(@NotNull Path pluginPath, @Nullable Logger logger) {
        return load(pluginPath, null, logger);
    }

    /**
     * Load a single plugin JAR file with expected name and logging.
     * <p>
     * If the expected plugin name is provided and a plugin with that name is already
     * loaded, returns the existing plugin without attempting to load again.
     *
     * @param pluginPath the path to the plugin JAR
     * @param expectedName the expected plugin name (for duplicate detection), or null
     * @param logger optional logger for status messages
     * @return the loaded Plugin instance, or null if loading failed
     */
    @Nullable
    public static Plugin load(@NotNull Path pluginPath, @Nullable String expectedName, @Nullable Logger logger) {
        File pluginFile = pluginPath.toFile();

        if (!pluginFile.exists()) {
            if (logger != null) {
                logger.warning("[Hopper] Plugin file not found: " + pluginPath);
            }
            return null;
        }

        if (!pluginFile.getName().endsWith(".jar")) {
            if (logger != null) {
                logger.warning("[Hopper] Not a JAR file: " + pluginPath);
            }
            return null;
        }

        PluginManager pm = Bukkit.getPluginManager();

        // Check if plugin is already loaded by name (most reliable check)
        if (expectedName != null) {
            Plugin existing = pm.getPlugin(expectedName);
            if (existing != null) {
                if (logger != null) {
                    logger.info("[Hopper] Plugin already loaded: " + existing.getName() + " v" + existing.getDescription().getVersion());
                }
                return existing;
            }
        }

        // Also check by file path as fallback
        try {
            for (Plugin p : pm.getPlugins()) {
                File pFile = getPluginFile(p);
                if (pFile != null && pFile.getAbsolutePath().equals(pluginFile.getAbsolutePath())) {
                    if (logger != null) {
                        logger.info("[Hopper] Plugin already loaded: " + p.getName());
                    }
                    return p;
                }
            }
        } catch (Exception ignored) {
            // Continue with loading attempt
        }

        try {
            if (logger != null) {
                logger.info("[Hopper] Loading plugin: " + pluginFile.getName());
            }

            Plugin plugin = pm.loadPlugin(pluginFile);
            if (plugin != null) {
                // Call onLoad
                plugin.onLoad();

                // Enable the plugin
                pm.enablePlugin(plugin);

                if (logger != null) {
                    logger.info("[Hopper] Successfully loaded: " + plugin.getName() + " v" + plugin.getDescription().getVersion());
                }
                return plugin;
            }
        } catch (InvalidPluginException e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "[Hopper] Invalid plugin: " + pluginFile.getName(), e);
            }
        } catch (InvalidDescriptionException e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "[Hopper] Invalid plugin description: " + pluginFile.getName(), e);
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "[Hopper] Failed to load plugin: " + pluginFile.getName(), e);
            }
        }

        return null;
    }

    /**
     * Load multiple plugin JAR files.
     *
     * @param pluginPaths the paths to the plugin JARs
     * @return the load result containing loaded and failed plugins
     */
    @NotNull
    public static LoadResult loadAll(@NotNull List<Path> pluginPaths) {
        return loadAll(pluginPaths, null);
    }

    /**
     * Load multiple plugin JAR files with logging.
     *
     * @param pluginPaths the paths to the plugin JARs
     * @param logger optional logger for status messages
     * @return the load result containing loaded and failed plugins
     */
    @NotNull
    public static LoadResult loadAll(@NotNull List<Path> pluginPaths, @Nullable Logger logger) {
        List<LoadedPlugin> loaded = new ArrayList<>();
        List<FailedPlugin> failed = new ArrayList<>();

        for (Path path : pluginPaths) {
            Plugin plugin = load(path, logger);
            if (plugin != null) {
                loaded.add(new LoadedPlugin(
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    path
                ));
            } else {
                failed.add(new FailedPlugin(path, "Failed to load plugin"));
            }
        }

        return new LoadResult(loaded, failed);
    }

    /**
     * Load multiple plugins with their expected names.
     * <p>
     * This method is preferred over {@link #loadAll(List, Logger)} when the expected
     * plugin names are known, as it provides better duplicate detection.
     *
     * @param plugins the plugins to load with their names and paths
     * @param logger optional logger for status messages
     * @return the load result containing loaded and failed plugins
     */
    @NotNull
    public static LoadResult loadAllWithNames(@NotNull List<PluginToLoad> plugins, @Nullable Logger logger) {
        return loadAllWithNames(plugins, null, logger);
    }

    /**
     * Load multiple plugins with their expected names, using file-based coordination.
     * <p>
     * This method acquires a file lock before loading to prevent race conditions when
     * multiple plugins using Hopper try to load the same dependency simultaneously.
     *
     * @param plugins the plugins to load with their names and paths
     * @param coordinationDir the .hopper directory for file locking (null to skip coordination)
     * @param logger optional logger for status messages
     * @return the load result containing loaded and failed plugins
     */
    @NotNull
    public static LoadResult loadAllWithNames(@NotNull List<PluginToLoad> plugins,
                                               @Nullable Path coordinationDir,
                                               @Nullable Logger logger) {
        if (coordinationDir == null) {
            return loadAllWithNamesInternal(plugins, logger);
        }

        // Use file-based coordination to prevent race conditions
        try (HopperCoordinator coordinator = HopperCoordinator.acquire(coordinationDir)) {
            return loadAllWithNamesInternal(plugins, logger);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "[Hopper] Failed to acquire coordination lock, proceeding without lock", e);
            }
            // Fall back to uncoordinated loading
            return loadAllWithNamesInternal(plugins, logger);
        }
    }

    @NotNull
    private static LoadResult loadAllWithNamesInternal(@NotNull List<PluginToLoad> plugins, @Nullable Logger logger) {
        List<LoadedPlugin> loaded = new ArrayList<>();
        List<FailedPlugin> failed = new ArrayList<>();

        for (PluginToLoad toLoad : plugins) {
            Plugin plugin = load(toLoad.path(), toLoad.name(), logger);
            if (plugin != null) {
                loaded.add(new LoadedPlugin(
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    toLoad.path()
                ));
            } else {
                failed.add(new FailedPlugin(toLoad.path(), "Failed to load plugin"));
            }
        }

        return new LoadResult(loaded, failed);
    }

    /**
     * Check if a plugin with the given name is currently loaded.
     *
     * @param pluginName the plugin name
     * @return true if the plugin is loaded
     */
    public static boolean isLoaded(@NotNull String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    /**
     * Get the JAR file for a loaded plugin.
     */
    @Nullable
    private static File getPluginFile(Plugin plugin) {
        try {
            // Try to get the plugin file via reflection
            // This works for most Bukkit implementations
            java.lang.reflect.Method getFile = plugin.getClass().getMethod("getFile");
            getFile.setAccessible(true);
            Object result = getFile.invoke(plugin);
            if (result instanceof File) {
                return (File) result;
            }
        } catch (Exception ignored) {
            // Method may not exist or not be accessible
        }

        // Fallback: try to get from the plugin's data folder
        File dataFolder = plugin.getDataFolder();
        if (dataFolder != null) {
            File pluginsDir = dataFolder.getParentFile();
            if (pluginsDir != null) {
                File jarFile = new File(pluginsDir, plugin.getName() + ".jar");
                if (jarFile.exists()) {
                    return jarFile;
                }
            }
        }

        return null;
    }
}
