package md.thomas.hopper.coordination;

import md.thomas.hopper.Dependency;
import md.thomas.hopper.util.JsonParser;
import md.thomas.hopper.version.UpdatePolicy;
import md.thomas.hopper.version.Version;
import md.thomas.hopper.version.VersionConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Tracks which plugins registered which dependencies and merges their constraints.
 * <p>
 * Stored in {@code plugins/.hopper/registry.json}
 */
public final class Registry {
    
    private static final int VERSION = 1;
    
    private final Map<String, DependencyRegistration> registrations = new LinkedHashMap<>();
    
    public Registry() {
    }
    
    /**
     * Register a plugin's dependencies, merging with existing registrations.
     */
    public void registerPlugin(@NotNull String pluginName, @NotNull List<Dependency> dependencies) {
        for (Dependency dep : dependencies) {
            String depName = dep.name();
            DependencyRegistration reg = registrations.computeIfAbsent(depName, 
                k -> new DependencyRegistration(depName));
            reg.addConstraint(pluginName, dep);
        }
    }
    
    /**
     * Get the merged constraint for a dependency (intersection of all plugins' constraints).
     *
     * @param dependencyName the dependency name
     * @return merged constraint, or null if not registered
     */
    public @Nullable VersionConstraint getMergedConstraint(@NotNull String dependencyName) {
        DependencyRegistration reg = registrations.get(dependencyName);
        return reg != null ? reg.getMergedConstraint() : null;
    }
    
    /**
     * Get all plugins that requested a dependency.
     */
    public @NotNull List<String> getRequestingPlugins(@NotNull String dependencyName) {
        DependencyRegistration reg = registrations.get(dependencyName);
        return reg != null ? new ArrayList<>(reg.requestedBy) : Collections.emptyList();
    }
    
    /**
     * Parse from JSON.
     */
    public static Registry fromJson(String json) {
        Registry registry = new Registry();
        
        Map<String, Object> root = JsonParser.parseObject(json);
        if (root == null) {
            return registry;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> regs = (Map<String, Object>) root.get("registrations");
        if (regs == null) {
            return registry;
        }
        
        for (Map.Entry<String, Object> entry : regs.entrySet()) {
            String depName = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> regData = (Map<String, Object>) entry.getValue();
            
            DependencyRegistration reg = new DependencyRegistration(depName);
            
            @SuppressWarnings("unchecked")
            List<String> requestedBy = (List<String>) regData.get("requestedBy");
            if (requestedBy != null) {
                reg.requestedBy.addAll(requestedBy);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> constraints = (List<Map<String, Object>>) regData.get("constraints");
            if (constraints != null) {
                for (Map<String, Object> c : constraints) {
                    String plugin = (String) c.get("plugin");
                    String minVer = (String) c.get("minVersion");
                    String policyStr = (String) c.get("updatePolicy");
                    
                    PluginConstraint pc = new PluginConstraint();
                    pc.plugin = plugin;
                    if (minVer != null) {
                        pc.constraint = VersionConstraint.atLeast(minVer);
                    }
                    if (policyStr != null) {
                        try {
                            pc.updatePolicy = UpdatePolicy.valueOf(policyStr);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    reg.constraints.add(pc);
                }
            }
            
            registry.registrations.put(depName, reg);
        }
        
        return registry;
    }
    
    /**
     * Serialize to JSON.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"generated\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"registrations\": {\n");
        
        boolean first = true;
        for (Map.Entry<String, DependencyRegistration> entry : registrations.entrySet()) {
            if (!first) sb.append(",\n");
            first = false;
            
            DependencyRegistration reg = entry.getValue();
            sb.append("    \"").append(JsonParser.escape(entry.getKey())).append("\": {\n");
            
            // requestedBy
            sb.append("      \"requestedBy\": [");
            sb.append(String.join(", ", reg.requestedBy.stream()
                .map(s -> "\"" + JsonParser.escape(s) + "\"")
                .toArray(String[]::new)));
            sb.append("],\n");
            
            // constraints
            sb.append("      \"constraints\": [\n");
            boolean firstC = true;
            for (PluginConstraint pc : reg.constraints) {
                if (!firstC) sb.append(",\n");
                firstC = false;
                sb.append("        { \"plugin\": \"").append(JsonParser.escape(pc.plugin)).append("\"");
                if (pc.constraint != null && pc.constraint.getMin() != null) {
                    sb.append(", \"minVersion\": \"").append(pc.constraint.getMin()).append("\"");
                }
                if (pc.updatePolicy != null) {
                    sb.append(", \"updatePolicy\": \"").append(pc.updatePolicy).append("\"");
                }
                sb.append(" }");
            }
            sb.append("\n      ],\n");
            
            // mergedConstraint
            VersionConstraint merged = reg.getMergedConstraint();
            if (merged != null) {
                sb.append("      \"mergedConstraint\": { ");
                if (merged.getMin() != null) {
                    sb.append("\"minVersion\": \"").append(merged.getMin()).append("\"");
                }
                sb.append(" }\n");
            } else {
                sb.append("      \"mergedConstraint\": null\n");
            }
            
            sb.append("    }");
        }
        
        sb.append("\n  }\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    // ========== Inner Classes ==========
    
    private static class DependencyRegistration {
        final String name;
        final Set<String> requestedBy = new LinkedHashSet<>();
        final List<PluginConstraint> constraints = new ArrayList<>();
        
        DependencyRegistration(String name) {
            this.name = name;
        }
        
        void addConstraint(String pluginName, Dependency dep) {
            requestedBy.add(pluginName);
            
            // Remove old constraint from this plugin if exists
            constraints.removeIf(c -> c.plugin.equals(pluginName));
            
            PluginConstraint pc = new PluginConstraint();
            pc.plugin = pluginName;
            pc.constraint = dep.constraint();
            pc.updatePolicy = dep.updatePolicy();
            constraints.add(pc);
        }
        
        @Nullable VersionConstraint getMergedConstraint() {
            if (constraints.isEmpty()) {
                return null;
            }
            
            // Start with the first constraint
            VersionConstraint merged = constraints.get(0).constraint;
            if (merged == null) {
                merged = VersionConstraint.latest();
            }
            
            // Merge with remaining constraints
            for (int i = 1; i < constraints.size(); i++) {
                VersionConstraint other = constraints.get(i).constraint;
                if (other == null) {
                    continue;
                }
                
                VersionConstraint newMerged = merged.merge(other);
                if (newMerged == null) {
                    // Incompatible constraints - use higher minimum
                    // This is the "warn and use higher" strategy
                    Version thisMin = merged.getMin();
                    Version otherMin = other.getMin();
                    
                    if (thisMin != null && otherMin != null) {
                        merged = thisMin.compareTo(otherMin) > 0 
                            ? VersionConstraint.atLeast(thisMin)
                            : VersionConstraint.atLeast(otherMin);
                    } else if (otherMin != null) {
                        merged = VersionConstraint.atLeast(otherMin);
                    }
                    // If thisMin != null but otherMin is null, keep merged
                } else {
                    merged = newMerged;
                }
            }
            
            return merged;
        }
    }
    
    private static class PluginConstraint {
        String plugin;
        VersionConstraint constraint;
        UpdatePolicy updatePolicy;
    }
}
