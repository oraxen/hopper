package md.thomas.hopper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server platform types for dependency resolution.
 * <p>
 * When set to {@link #AUTO}, the platform is detected at runtime based on
 * available server classes.
 */
public enum Platform {
    /**
     * Auto-detect the platform at runtime.
     * Detection order: Folia → Paper → Spigot → Bukkit
     */
    AUTO(null),

    /**
     * Folia - Paper fork with regionized multithreading.
     */
    FOLIA("folia"),

    /**
     * Paper - Performance-focused Spigot fork.
     */
    PAPER("paper"),

    /**
     * Spigot - CraftBukkit fork with optimizations.
     */
    SPIGOT("spigot"),

    /**
     * Bukkit - Base server API.
     */
    BUKKIT("bukkit"),

    /**
     * Purpur - Paper fork with extra features.
     */
    PURPUR("purpur"),

    /**
     * Velocity - Modern proxy server.
     */
    VELOCITY("velocity"),

    /**
     * BungeeCord - Proxy server.
     */
    BUNGEECORD("bungeecord"),

    /**
     * Waterfall - BungeeCord fork by PaperMC.
     */
    WATERFALL("waterfall");

    private final @Nullable String id;

    Platform(@Nullable String id) {
        this.id = id;
    }

    /**
     * @return the platform identifier used in API queries (e.g., "paper", "spigot")
     */
    public @Nullable String id() {
        return id;
    }

    /**
     * Get the Hangar platform name.
     */
    public @NotNull String hangarPlatform() {
        return switch (this) {
            case FOLIA, PAPER, PURPUR -> "PAPER";
            case SPIGOT, BUKKIT -> "PAPER"; // Hangar doesn't have separate Spigot, Paper works
            case VELOCITY -> "VELOCITY";
            case BUNGEECORD, WATERFALL -> "WATERFALL";
            case AUTO -> "PAPER"; // Fallback, should be resolved before use
        };
    }

    /**
     * Get compatible Modrinth loaders for this platform.
     */
    public @NotNull String[] modrinthLoaders() {
        return switch (this) {
            case FOLIA -> new String[]{"folia", "paper", "spigot", "bukkit"};
            case PAPER -> new String[]{"paper", "spigot", "bukkit"};
            case PURPUR -> new String[]{"purpur", "paper", "spigot", "bukkit"};
            case SPIGOT -> new String[]{"spigot", "bukkit"};
            case BUKKIT -> new String[]{"bukkit"};
            case VELOCITY -> new String[]{"velocity"};
            case BUNGEECORD -> new String[]{"bungeecord"};
            case WATERFALL -> new String[]{"waterfall", "bungeecord"};
            case AUTO -> new String[]{"paper", "spigot", "bukkit", "purpur", "folia"};
        };
    }

    // Cached detected platform
    private static volatile Platform detected = null;

    /**
     * Detect the current server platform.
     * Result is cached after first detection.
     *
     * @return the detected platform, or BUKKIT if detection fails
     */
    public static @NotNull Platform detect() {
        if (detected != null) {
            return detected;
        }

        synchronized (Platform.class) {
            if (detected != null) {
                return detected;
            }

            detected = doDetect();
            return detected;
        }
    }

    private static @NotNull Platform doDetect() {
        // Check for Folia first (most specific)
        if (classExists("io.papermc.paper.threadedregions.RegionizedServer")) {
            return FOLIA;
        }

        // Check for Purpur
        if (classExists("org.purpurmc.purpur.PurpurConfig")) {
            return PURPUR;
        }

        // Check for Paper
        if (classExists("io.papermc.paper.configuration.Configuration")) {
            return PAPER;
        }

        // Check for older Paper versions
        if (classExists("com.destroystokyo.paper.PaperConfig")) {
            return PAPER;
        }

        // Check for Spigot
        if (classExists("org.spigotmc.SpigotConfig")) {
            return SPIGOT;
        }

        // Check for Velocity
        if (classExists("com.velocitypowered.api.proxy.ProxyServer")) {
            return VELOCITY;
        }

        // Check for Waterfall
        if (classExists("io.github.waterfallmc.waterfall.conf.WaterfallConfiguration")) {
            return WATERFALL;
        }

        // Check for BungeeCord
        if (classExists("net.md_5.bungee.api.ProxyServer")) {
            return BUNGEECORD;
        }

        // Default to Bukkit
        return BUKKIT;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Resolve AUTO to the actual detected platform.
     *
     * @return the resolved platform (never AUTO)
     */
    public @NotNull Platform resolve() {
        return this == AUTO ? detect() : this;
    }
}
