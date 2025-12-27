package md.thomas.hopper.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible version parser that handles non-standard Minecraft plugin versions.
 * <p>
 * Supports formats like:
 * <ul>
 *   <li>{@code 5.4.0} - Standard semver</li>
 *   <li>{@code R4.0.9} - Prefixed (ModelEngine style)</li>
 *   <li>{@code 5.4.0-SNAPSHOT} - With qualifier</li>
 *   <li>{@code v2.11} - Simple prefix</li>
 *   <li>{@code build-123} - Build number only</li>
 *   <li>{@code 2024.12.20} - Calendar versioning</li>
 * </ul>
 */
public final class Version implements Comparable<Version> {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "^([a-zA-Z]*[-]?)?" +           // Optional prefix (R, v, build-, etc.)
        "([0-9]+(?:[._][0-9]+)*)" +      // Numeric components (1.2.3 or 1_2_3 or 1.2_3)
        "(?:[-.]?([a-zA-Z][a-zA-Z0-9]*(?:[-._][a-zA-Z0-9]+)*))?" + // Optional qualifier
        "(?:[+](.+))?" +                  // Optional build metadata after +
        "$"
    );
    
    private static final Pattern BUILD_NUMBER_PATTERN = Pattern.compile(
        "^(?:build[-.]?)?#?(\\d+)$",
        Pattern.CASE_INSENSITIVE
    );
    
    // Qualifier ordering: release (no qualifier) > RC > beta > alpha > SNAPSHOT
    private static final List<String> QUALIFIER_ORDER = Arrays.asList(
        "snapshot", "dev", "alpha", "beta", "rc", "cr", "final", "ga", "release", ""
    );
    
    private final String raw;
    private final @Nullable String prefix;
    private final int[] components;
    private final @Nullable String qualifier;
    private final @Nullable Integer buildNumber;
    private final @Nullable String buildMetadata;
    
    private Version(String raw, @Nullable String prefix, int[] components, 
                    @Nullable String qualifier, @Nullable Integer buildNumber,
                    @Nullable String buildMetadata) {
        this.raw = raw;
        this.prefix = prefix;
        this.components = components;
        this.qualifier = qualifier;
        this.buildNumber = buildNumber;
        this.buildMetadata = buildMetadata;
    }
    
    /**
     * Parse a version string into a Version object.
     *
     * @param raw the version string to parse
     * @return the parsed Version
     * @throws IllegalArgumentException if the version string cannot be parsed
     */
    public static Version parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Version string cannot be null or blank");
        }
        
        String trimmed = raw.trim();
        
        // Check for build-number-only format
        Matcher buildMatcher = BUILD_NUMBER_PATTERN.matcher(trimmed);
        if (buildMatcher.matches()) {
            int buildNum = Integer.parseInt(buildMatcher.group(1));
            return new Version(trimmed, "build-", new int[0], null, buildNum, null);
        }
        
        Matcher matcher = VERSION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            // Fallback: treat the whole string as a single component if it contains digits
            if (trimmed.matches(".*\\d+.*")) {
                List<Integer> nums = new ArrayList<>();
                StringBuilder current = new StringBuilder();
                for (char c : trimmed.toCharArray()) {
                    if (Character.isDigit(c)) {
                        current.append(c);
                    } else if (current.length() > 0) {
                        nums.add(Integer.parseInt(current.toString()));
                        current.setLength(0);
                    }
                }
                if (current.length() > 0) {
                    nums.add(Integer.parseInt(current.toString()));
                }
                if (!nums.isEmpty()) {
                    return new Version(trimmed, null, nums.stream().mapToInt(i -> i).toArray(), null, null, null);
                }
            }
            throw new IllegalArgumentException("Cannot parse version: " + raw);
        }
        
        String prefix = matcher.group(1);
        if (prefix != null && prefix.isEmpty()) {
            prefix = null;
        }
        
        String numericPart = matcher.group(2);
        String[] parts = numericPart.split("[._]");
        int[] components = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            components[i] = Integer.parseInt(parts[i]);
        }
        
        String qualifier = matcher.group(3);
        String buildMetadata = matcher.group(4);
        
        // Extract build number from qualifier if present (e.g., "beta.2" or "RC1")
        Integer buildNumber = null;
        if (qualifier != null) {
            Matcher qualBuildMatcher = Pattern.compile("(\\d+)$").matcher(qualifier);
            if (qualBuildMatcher.find()) {
                buildNumber = Integer.parseInt(qualBuildMatcher.group(1));
            }
        }
        
        return new Version(trimmed, prefix, components, qualifier, buildNumber, buildMetadata);
    }
    
    /**
     * Try to parse a version string, returning null if parsing fails.
     */
    public static @Nullable Version tryParse(String raw) {
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * @return the original version string
     */
    public String raw() {
        return raw;
    }
    
    /**
     * @return the version prefix (e.g., "R", "v", "build-"), or null if none
     */
    public @Nullable String prefix() {
        return prefix;
    }
    
    /**
     * @return the numeric version components
     */
    public int[] components() {
        return components.clone();
    }
    
    /**
     * @return the major version (first component), or 0 if no components
     */
    public int major() {
        return components.length > 0 ? components[0] : 0;
    }
    
    /**
     * @return the minor version (second component), or 0 if not present
     */
    public int minor() {
        return components.length > 1 ? components[1] : 0;
    }
    
    /**
     * @return the patch version (third component), or 0 if not present
     */
    public int patch() {
        return components.length > 2 ? components[2] : 0;
    }
    
    /**
     * @return the version qualifier (e.g., "SNAPSHOT", "beta"), or null if release
     */
    public @Nullable String qualifier() {
        return qualifier;
    }
    
    /**
     * @return true if this is a pre-release version (has qualifier like SNAPSHOT, beta, etc.)
     */
    public boolean isPreRelease() {
        if (qualifier == null) {
            return false;
        }
        String lower = qualifier.toLowerCase();
        return lower.contains("snapshot") || lower.contains("alpha") || 
               lower.contains("beta") || lower.contains("dev") ||
               lower.contains("rc") || lower.contains("cr");
    }
    
    /**
     * @return the build number if present
     */
    public @Nullable Integer buildNumber() {
        return buildNumber;
    }
    
    /**
     * Check if this version is compatible with an update policy relative to a baseline.
     *
     * @param baseline the baseline version
     * @param policy the update policy
     * @return true if this version is allowed by the policy
     */
    public boolean isAllowedBy(Version baseline, UpdatePolicy policy) {
        if (policy == UpdatePolicy.NONE) {
            return this.equals(baseline);
        }
        
        // Must be >= baseline
        if (this.compareTo(baseline) < 0) {
            return false;
        }
        
        switch (policy) {
            case PATCH:
                return this.major() == baseline.major() && this.minor() == baseline.minor();
            case MINOR:
                return this.major() == baseline.major();
            case MAJOR:
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public int compareTo(@NotNull Version other) {
        // Compare numeric components
        int maxLen = Math.max(this.components.length, other.components.length);
        for (int i = 0; i < maxLen; i++) {
            int thisComp = i < this.components.length ? this.components[i] : 0;
            int otherComp = i < other.components.length ? other.components[i] : 0;
            if (thisComp != otherComp) {
                return Integer.compare(thisComp, otherComp);
            }
        }
        
        // If components are equal, compare qualifiers
        // No qualifier (release) > any qualifier
        if (this.qualifier == null && other.qualifier == null) {
            // Compare build numbers if both have them
            if (this.buildNumber != null && other.buildNumber != null) {
                return Integer.compare(this.buildNumber, other.buildNumber);
            }
            return 0;
        }
        if (this.qualifier == null) {
            return 1; // Release > pre-release
        }
        if (other.qualifier == null) {
            return -1;
        }
        
        // Compare qualifier ordering
        String thisQualLower = this.qualifier.toLowerCase().replaceAll("[^a-z]", "");
        String otherQualLower = other.qualifier.toLowerCase().replaceAll("[^a-z]", "");
        
        int thisQualIdx = findQualifierIndex(thisQualLower);
        int otherQualIdx = findQualifierIndex(otherQualLower);
        
        if (thisQualIdx != otherQualIdx) {
            return Integer.compare(thisQualIdx, otherQualIdx);
        }
        
        // Same qualifier type, compare build numbers
        if (this.buildNumber != null && other.buildNumber != null) {
            return Integer.compare(this.buildNumber, other.buildNumber);
        }
        
        // Fallback to string comparison
        return this.qualifier.compareToIgnoreCase(other.qualifier);
    }
    
    private static int findQualifierIndex(String qual) {
        for (int i = 0; i < QUALIFIER_ORDER.size(); i++) {
            if (qual.contains(QUALIFIER_ORDER.get(i))) {
                return i;
            }
        }
        return QUALIFIER_ORDER.size() / 2; // Unknown qualifiers go in the middle
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Version)) return false;
        Version other = (Version) obj;
        return Arrays.equals(components, other.components) &&
               Objects.equals(qualifier, other.qualifier) &&
               Objects.equals(buildNumber, other.buildNumber);
    }
    
    @Override
    public int hashCode() {
        int result = Arrays.hashCode(components);
        result = 31 * result + Objects.hashCode(qualifier);
        result = 31 * result + Objects.hashCode(buildNumber);
        return result;
    }
    
    @Override
    public String toString() {
        return raw;
    }
}
