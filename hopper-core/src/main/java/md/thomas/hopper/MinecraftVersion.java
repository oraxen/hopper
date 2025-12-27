package md.thomas.hopper;

import org.jetbrains.annotations.Nullable;

/**
 * Global Minecraft version configuration for dependency filtering.
 * <p>
 * When a Minecraft version is set, dependencies from sources that support
 * version filtering (Modrinth, Hangar) will automatically filter to only
 * return versions compatible with that MC version.
 * <p>
 * <h2>Priority Order</h2>
 * <ol>
 *   <li>Per-dependency explicit setting via {@code .minecraftVersion("1.21")}</li>
 *   <li>Global setting via {@link #set(String)}</li>
 *   <li>No filtering (returns versions for all MC versions)</li>
 * </ol>
 * <p>
 * <h2>Usage</h2>
 * <pre>{@code
 * // In BukkitHopper, this is set automatically from Bukkit.getMinecraftVersion()
 * // For manual use in hopper-core:
 * MinecraftVersion.set("1.21");
 *
 * // Dependencies without explicit minecraftVersion() will use this global value
 * Dependency.modrinth("packetevents").build(); // Filters for 1.21
 *
 * // Explicit setting overrides global
 * Dependency.modrinth("old-plugin").minecraftVersion("1.19.4").build(); // Uses 1.19.4
 * }</pre>
 */
public final class MinecraftVersion {

    private static volatile String globalVersion = null;

    private MinecraftVersion() {}

    /**
     * Set the global Minecraft version for dependency filtering.
     * <p>
     * This is typically called automatically by BukkitHopper using
     * the server's detected Minecraft version.
     *
     * @param version the Minecraft version (e.g., "1.21", "1.20.4"), or null to disable filtering
     */
    public static void set(@Nullable String version) {
        globalVersion = version;
    }

    /**
     * Get the global Minecraft version.
     *
     * @return the global Minecraft version, or null if not set
     */
    public static @Nullable String get() {
        return globalVersion;
    }

    /**
     * Check if a global Minecraft version is set.
     *
     * @return true if a global version is configured
     */
    public static boolean isSet() {
        return globalVersion != null;
    }

    /**
     * Clear the global Minecraft version setting.
     */
    public static void clear() {
        globalVersion = null;
    }

    /**
     * Get the effective Minecraft version for a dependency.
     * <p>
     * Returns the dependency's explicit version if set, otherwise the global version.
     *
     * @param dependency the dependency to check
     * @return the effective MC version, or null if neither is set
     */
    public static @Nullable String getEffective(@Nullable Dependency dependency) {
        if (dependency != null && dependency.minecraftVersion() != null) {
            return dependency.minecraftVersion();
        }
        return globalVersion;
    }
}
