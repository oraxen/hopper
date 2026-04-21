package md.thomas.hopper.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads minimal metadata (name, version) from the <code>plugin.yml</code> or
 * <code>paper-plugin.yml</code> inside a plugin jar without pulling in a full
 * YAML parser. Used to detect user-installed plugins so Hopper never overwrites
 * a jar a server admin put in place themselves.
 */
public final class PluginYamlReader {

    private static final String[] DESCRIPTOR_ENTRIES = {"paper-plugin.yml", "plugin.yml"};

    // Matches top-level `name: value` / `name: "value"` / `name: 'value'`
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^name\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^#\\s][^#]*?))\\s*(?:#.*)?$"
    );
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^version\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^#\\s][^#]*?))\\s*(?:#.*)?$"
    );

    private PluginYamlReader() {}

    /**
     * Descriptor data extracted from a plugin jar.
     */
    public record Descriptor(@NotNull String name, @Nullable String version) {}

    /**
     * Read the plugin descriptor from a jar, or null if none is found.
     */
    @Nullable
    public static Descriptor read(@NotNull Path jarPath) {
        if (!Files.isRegularFile(jarPath)) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (String entryName : DESCRIPTOR_ENTRIES) {
                JarEntry entry = jar.getJarEntry(entryName);
                if (entry == null) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    Descriptor d = parse(is);
                    if (d != null) return d;
                }
            }
        } catch (IOException e) {
            // Malformed/unreadable jar — treat as not a plugin.
        }
        return null;
    }

    /**
     * Find the first jar in <code>pluginsFolder</code> whose descriptor name
     * matches <code>pluginName</code> (case-insensitive).
     */
    @Nullable
    public static Path findInstalledPlugin(@NotNull Path pluginsFolder, @NotNull String pluginName) {
        if (!Files.isDirectory(pluginsFolder)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsFolder, "*.jar")) {
            for (Path jar : stream) {
                Descriptor d = read(jar);
                if (d != null && d.name().equalsIgnoreCase(pluginName)) {
                    return jar;
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return null;
    }

    @Nullable
    private static Descriptor parse(InputStream is) throws IOException {
        String name = null;
        String version = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Only consider top-level keys (no leading whitespace, not a list/comment)
                if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t'
                        || line.charAt(0) == '#' || line.charAt(0) == '-') {
                    continue;
                }
                if (name == null) {
                    String v = match(NAME_PATTERN, line);
                    if (v != null) {
                        name = v;
                        continue;
                    }
                }
                if (version == null) {
                    String v = match(VERSION_PATTERN, line);
                    if (v != null) {
                        version = v;
                    }
                }
                if (name != null && version != null) break;
            }
        }
        if (name == null) return null;
        return new Descriptor(name, version);
    }

    @Nullable
    private static String match(Pattern p, String line) {
        Matcher m = p.matcher(line);
        if (!m.matches()) return null;
        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g != null) return g.trim();
        }
        return null;
    }
}
