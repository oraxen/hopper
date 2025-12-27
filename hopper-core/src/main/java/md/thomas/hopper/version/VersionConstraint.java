package md.thomas.hopper.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents version constraints for dependency resolution.
 * <p>
 * Supports:
 * <ul>
 *   <li>Exact: {@code 5.4.0}</li>
 *   <li>Minimum: {@code >=5.0.0}</li>
 *   <li>Range: {@code >=5.0.0 <6.0.0}</li>
 *   <li>Latest: {@code *} or empty</li>
 * </ul>
 */
public final class VersionConstraint {
    
    private static final Pattern CONSTRAINT_PART = Pattern.compile(
        "([<>=!]+)?\\s*([^\\s<>=!]+)"
    );
    
    private final @Nullable Version exact;
    private final @Nullable Version min;
    private final boolean minInclusive;
    private final @Nullable Version max;
    private final boolean maxInclusive;
    private final boolean latest;
    
    private VersionConstraint(@Nullable Version exact, @Nullable Version min, boolean minInclusive,
                              @Nullable Version max, boolean maxInclusive, boolean latest) {
        this.exact = exact;
        this.min = min;
        this.minInclusive = minInclusive;
        this.max = max;
        this.maxInclusive = maxInclusive;
        this.latest = latest;
    }
    
    /**
     * Create a constraint for an exact version match.
     */
    public static VersionConstraint exact(String version) {
        return new VersionConstraint(Version.parse(version), null, false, null, false, false);
    }
    
    /**
     * Create a constraint for an exact version match.
     */
    public static VersionConstraint exact(Version version) {
        return new VersionConstraint(version, null, false, null, false, false);
    }
    
    /**
     * Create a constraint for a minimum version (inclusive).
     */
    public static VersionConstraint atLeast(String minVersion) {
        return new VersionConstraint(null, Version.parse(minVersion), true, null, false, false);
    }
    
    /**
     * Create a constraint for a minimum version (inclusive).
     */
    public static VersionConstraint atLeast(Version minVersion) {
        return new VersionConstraint(null, minVersion, true, null, false, false);
    }
    
    /**
     * Create a constraint for a version range (min inclusive, max exclusive by default).
     */
    public static VersionConstraint range(String minVersion, String maxVersion) {
        return new VersionConstraint(null, Version.parse(minVersion), true, 
                                     Version.parse(maxVersion), false, false);
    }
    
    /**
     * Create a constraint for a version range with explicit inclusivity.
     */
    public static VersionConstraint range(Version min, boolean minInclusive, 
                                          Version max, boolean maxInclusive) {
        return new VersionConstraint(null, min, minInclusive, max, maxInclusive, false);
    }
    
    /**
     * Create a constraint that matches any version (latest).
     */
    public static VersionConstraint latest() {
        return new VersionConstraint(null, null, false, null, false, true);
    }
    
    /**
     * Parse a constraint expression.
     * <p>
     * Supported formats:
     * <ul>
     *   <li>{@code 5.4.0} - exact match</li>
     *   <li>{@code >=5.0.0} - minimum (inclusive)</li>
     *   <li>{@code >5.0.0} - minimum (exclusive)</li>
     *   <li>{@code <6.0.0} - maximum (exclusive)</li>
     *   <li>{@code <=6.0.0} - maximum (inclusive)</li>
     *   <li>{@code >=5.0.0 <6.0.0} - range</li>
     *   <li>{@code *} or empty - latest</li>
     * </ul>
     */
    public static VersionConstraint parse(String expression) {
        if (expression == null || expression.isBlank() || expression.equals("*")) {
            return latest();
        }
        
        String trimmed = expression.trim();
        
        // Check for operators
        if (!trimmed.contains(">") && !trimmed.contains("<") && !trimmed.contains("=")) {
            // Simple version string - exact match
            return exact(trimmed);
        }
        
        Version min = null;
        boolean minInclusive = false;
        Version max = null;
        boolean maxInclusive = false;
        
        Matcher matcher = CONSTRAINT_PART.matcher(trimmed);
        while (matcher.find()) {
            String op = matcher.group(1);
            String ver = matcher.group(2);
            Version version = Version.parse(ver);
            
            if (op == null || op.isEmpty() || op.equals("=") || op.equals("==")) {
                // Exact match
                return exact(version);
            }
            
            switch (op) {
                case ">=":
                    min = version;
                    minInclusive = true;
                    break;
                case ">":
                    min = version;
                    minInclusive = false;
                    break;
                case "<=":
                    max = version;
                    maxInclusive = true;
                    break;
                case "<":
                    max = version;
                    maxInclusive = false;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operator: " + op);
            }
        }
        
        if (min == null && max == null) {
            throw new IllegalArgumentException("Could not parse constraint: " + expression);
        }
        
        return new VersionConstraint(null, min, minInclusive, max, maxInclusive, false);
    }
    
    /**
     * Check if a version satisfies this constraint.
     */
    public boolean isSatisfiedBy(Version version) {
        if (latest) {
            return true;
        }
        
        if (exact != null) {
            return version.equals(exact);
        }
        
        if (min != null) {
            int cmp = version.compareTo(min);
            if (minInclusive ? cmp < 0 : cmp <= 0) {
                return false;
            }
        }
        
        if (max != null) {
            int cmp = version.compareTo(max);
            if (maxInclusive ? cmp > 0 : cmp >= 0) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Select the best version from a list of available versions.
     * Returns the highest version that satisfies this constraint.
     *
     * @param available list of available versions
     * @return the best matching version, or null if none satisfy the constraint
     */
    public @Nullable Version selectBest(List<Version> available) {
        if (available == null || available.isEmpty()) {
            return null;
        }
        
        List<Version> sorted = new ArrayList<>(available);
        Collections.sort(sorted, Collections.reverseOrder()); // Highest first
        
        for (Version v : sorted) {
            if (isSatisfiedBy(v)) {
                return v;
            }
        }
        
        return null;
    }
    
    /**
     * Merge this constraint with another, returning the intersection.
     * The result satisfies both constraints.
     *
     * @param other the other constraint
     * @return merged constraint, or null if constraints are incompatible
     */
    public @Nullable VersionConstraint merge(VersionConstraint other) {
        if (this.latest && other.latest) {
            return latest();
        }
        
        // Exact constraints must match
        if (this.exact != null && other.exact != null) {
            return this.exact.equals(other.exact) ? this : null;
        }
        
        if (this.exact != null) {
            return other.isSatisfiedBy(this.exact) ? this : null;
        }
        
        if (other.exact != null) {
            return this.isSatisfiedBy(other.exact) ? other : null;
        }
        
        // Merge ranges - take highest min and lowest max
        Version newMin = null;
        boolean newMinInclusive = false;
        
        if (this.min != null && other.min != null) {
            int cmp = this.min.compareTo(other.min);
            if (cmp > 0) {
                newMin = this.min;
                newMinInclusive = this.minInclusive;
            } else if (cmp < 0) {
                newMin = other.min;
                newMinInclusive = other.minInclusive;
            } else {
                newMin = this.min;
                newMinInclusive = this.minInclusive && other.minInclusive;
            }
        } else {
            newMin = this.min != null ? this.min : other.min;
            newMinInclusive = this.min != null ? this.minInclusive : other.minInclusive;
        }
        
        Version newMax = null;
        boolean newMaxInclusive = false;
        
        if (this.max != null && other.max != null) {
            int cmp = this.max.compareTo(other.max);
            if (cmp < 0) {
                newMax = this.max;
                newMaxInclusive = this.maxInclusive;
            } else if (cmp > 0) {
                newMax = other.max;
                newMaxInclusive = other.maxInclusive;
            } else {
                newMax = this.max;
                newMaxInclusive = this.maxInclusive && other.maxInclusive;
            }
        } else {
            newMax = this.max != null ? this.max : other.max;
            newMaxInclusive = this.max != null ? this.maxInclusive : other.maxInclusive;
        }
        
        // Check if the merged range is valid
        if (newMin != null && newMax != null) {
            int cmp = newMin.compareTo(newMax);
            if (cmp > 0 || (cmp == 0 && !(newMinInclusive && newMaxInclusive))) {
                return null; // Invalid range
            }
        }
        
        return new VersionConstraint(null, newMin, newMinInclusive, newMax, newMaxInclusive, false);
    }
    
    /**
     * Create a constraint based on a baseline version and update policy.
     */
    public static VersionConstraint fromPolicy(Version baseline, UpdatePolicy policy) {
        switch (policy) {
            case NONE:
                return exact(baseline);
            case PATCH:
                // Allow X.Y.* where X.Y matches baseline
                Version patchMax = Version.parse(baseline.major() + "." + (baseline.minor() + 1) + ".0");
                return range(baseline, true, patchMax, false);
            case MINOR:
                // Allow X.*.* where X matches baseline
                Version minorMax = Version.parse((baseline.major() + 1) + ".0.0");
                return range(baseline, true, minorMax, false);
            case MAJOR:
            default:
                return atLeast(baseline);
        }
    }
    
    public boolean isLatest() {
        return latest;
    }
    
    public @Nullable Version getExact() {
        return exact;
    }
    
    public @Nullable Version getMin() {
        return min;
    }
    
    public boolean isMinInclusive() {
        return minInclusive;
    }
    
    public @Nullable Version getMax() {
        return max;
    }
    
    public boolean isMaxInclusive() {
        return maxInclusive;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VersionConstraint)) return false;
        VersionConstraint other = (VersionConstraint) obj;
        return latest == other.latest &&
               minInclusive == other.minInclusive &&
               maxInclusive == other.maxInclusive &&
               Objects.equals(exact, other.exact) &&
               Objects.equals(min, other.min) &&
               Objects.equals(max, other.max);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(exact, min, minInclusive, max, maxInclusive, latest);
    }
    
    @Override
    public String toString() {
        if (latest) {
            return "*";
        }
        if (exact != null) {
            return exact.toString();
        }
        
        StringBuilder sb = new StringBuilder();
        if (min != null) {
            sb.append(minInclusive ? ">=" : ">").append(min);
        }
        if (max != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(maxInclusive ? "<=" : "<").append(max);
        }
        return sb.toString();
    }
}
