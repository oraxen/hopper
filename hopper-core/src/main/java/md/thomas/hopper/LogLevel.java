package md.thomas.hopper;

/**
 * Controls the verbosity of Hopper's logging output.
 * <p>
 * <ul>
 *   <li><b>VERBOSE</b> - Full output including all processing steps, version fetching, downloads, and summaries</li>
 *   <li><b>NORMAL</b> (default) - Shows download progress and final loaded plugins summary</li>
 *   <li><b>QUIET</b> - Only shows the final result (which plugins were loaded)</li>
 *   <li><b>SILENT</b> - No output at all (for embedding in other plugins that handle their own logging)</li>
 * </ul>
 * <p>
 * Example output at each level:
 * <pre>
 * VERBOSE:
 *   [Oraxen] [Hopper] Detected Minecraft version: 1.21.1
 *   [Oraxen] Processing 2 dependency(ies) for Oraxen
 *   [Oraxen] Processing dependency: CommandAPI
 *   [Oraxen]   Fetching versions from MODRINTH...
 *   [Oraxen]   Selected version: 11.1.0
 *   [Oraxen]   Downloading from: https://cdn.modrinth.com/...
 *   [Oraxen]   Downloaded successfully: CommandAPI-11.1.0-Paper.jar
 *   [Oraxen] [Hopper] Loaded: CommandAPI 11.1.0, PacketEvents 2.11.1
 *
 * NORMAL:
 *   [Oraxen] [Hopper] Downloading CommandAPI 11.1.0...
 *   [Oraxen] [Hopper] Downloading PacketEvents 2.11.1...
 *   [Oraxen] [Hopper] Loaded: CommandAPI 11.1.0, PacketEvents 2.11.1
 *
 * QUIET:
 *   [Oraxen] [Hopper] Loaded: CommandAPI 11.1.0, PacketEvents 2.11.1
 *
 * SILENT:
 *   (no output)
 * </pre>
 */
public enum LogLevel {
    /**
     * Full output: all processing steps, version fetching, downloads, and summaries.
     */
    VERBOSE,

    /**
     * Default level: shows download progress and final loaded plugins summary.
     */
    NORMAL,

    /**
     * Minimal output: only shows the final result (which plugins were loaded).
     */
    QUIET,

    /**
     * No output at all. Use when embedding Hopper in plugins that handle their own logging.
     */
    SILENT;

    /**
     * Check if this level allows messages at the given detail level.
     *
     * @param requiredLevel the minimum level required to show the message
     * @return true if the message should be shown
     */
    public boolean allows(LogLevel requiredLevel) {
        // VERBOSE allows all, NORMAL allows NORMAL+, QUIET allows QUIET+, SILENT allows nothing
        return this.ordinal() <= requiredLevel.ordinal();
    }
}
