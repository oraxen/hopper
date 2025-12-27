package md.thomas.hopper.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * File utility methods.
 */
public final class FileUtils {
    
    private FileUtils() {}
    
    /**
     * Find files matching a glob pattern in a directory.
     *
     * @param directory the directory to search
     * @param pattern the glob pattern (e.g., "*.jar", "Plugin-*.jar")
     * @return list of matching paths
     */
    @NotNull
    public static List<Path> findFiles(@NotNull Path directory, @NotNull String pattern) {
        List<Path> results = new ArrayList<>();
        
        if (!Files.isDirectory(directory)) {
            return results;
        }
        
        // Convert glob to regex
        String regex = globToRegex(pattern);
        Pattern compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (compiled.matcher(path.getFileName().toString()).matches()) {
                    results.add(path);
                }
            }
        } catch (IOException e) {
            // Ignore and return empty list
        }
        
        return results;
    }
    
    /**
     * Find a single file matching a glob pattern.
     *
     * @param directory the directory to search
     * @param pattern the glob pattern
     * @return the first matching file, or null if none found
     */
    @Nullable
    public static Path findFile(@NotNull Path directory, @NotNull String pattern) {
        List<Path> files = findFiles(directory, pattern);
        return files.isEmpty() ? null : files.get(0);
    }
    
    /**
     * Check if a filename matches a glob pattern.
     *
     * @param filename the filename to check
     * @param pattern the glob pattern
     * @return true if matches
     */
    public static boolean matches(@NotNull String filename, @NotNull String pattern) {
        String regex = globToRegex(pattern);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(filename).matches();
    }
    
    /**
     * Delete a file or directory (recursively if directory).
     *
     * @param path the path to delete
     * @return true if deleted
     */
    public static boolean delete(@NotNull Path path) {
        if (!Files.exists(path)) {
            return true;
        }
        
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(path);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Ensure a directory exists.
     *
     * @param directory the directory path
     * @return the directory path
     */
    @NotNull
    public static Path ensureDirectory(@NotNull Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + directory, e);
        }
        return directory;
    }
    
    /**
     * Get the file extension (without the dot).
     *
     * @param path the file path
     * @return the extension, or empty string if none
     */
    @NotNull
    public static String getExtension(@NotNull Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }
    
    /**
     * Get the filename without extension.
     *
     * @param path the file path
     * @return the name without extension
     */
    @NotNull
    public static String getNameWithoutExtension(@NotNull Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
    
    /**
     * Convert a glob pattern to a regex pattern.
     */
    @NotNull
    private static String globToRegex(@NotNull String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }
        
        regex.append("$");
        return regex.toString();
    }
}
