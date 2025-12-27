# Hopper

**Plugin Dependency Loader for Minecraft Servers**

Hopper downloads plugin dependencies from Hangar, Modrinth, SpigotMC, and GitHub Releases at runtime—so you don't need to shade them.

## Features

- **Multiple Sources**: Download from Hangar, Modrinth, Spiget (SpigotMC), GitHub Releases, or direct URLs
- **Smart Version Resolution**: Supports exact versions, minimum versions, ranges, and update policies
- **Auto-Load Support**: Automatically load downloaded plugins at runtime—no server restart required!
- **Non-Standard Versions**: Handles formats like `R4.0.9`, `5.4.0-SNAPSHOT`, `build-123`, `2024.12.20`
- **Multi-Plugin Coordination**: Multiple plugins can shade Hopper and share the same downloaded dependencies
- **Lockfile Support**: Reproducible builds with `hopper.lock`
- **Minimal Dependencies**: Uses only JDK built-ins (no Gson, no external HTTP libraries)
- **Shade-Friendly**: Single package for easy relocation

## Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://repo.oraxen.com/releases")
}

dependencies {
    // Core (platform-agnostic)
    implementation("md.thomas.hopper:hopper-core:1.1.2")

    // Bukkit/Spigot
    implementation("md.thomas.hopper:hopper-bukkit:1.1.2")

    // Paper (with bootstrap support)
    implementation("md.thomas.hopper:hopper-paper:1.1.2")
}

// Shade and relocate
tasks.shadowJar {
    relocate("md.thomas.hopper", "your.package.hopper")
}
```

### Gradle (Groovy)

```groovy
repositories {
    maven { url 'https://repo.oraxen.com/releases' }
}

dependencies {
    implementation 'md.thomas.hopper:hopper-bukkit:1.1.2'
}
```

### Maven

```xml
<repository>
    <id>oraxen</id>
    <url>https://repo.oraxen.com/releases</url>
</repository>

<dependency>
    <groupId>md.thomas.hopper</groupId>
    <artifactId>hopper-bukkit</artifactId>
    <version>1.1.2</version>
</dependency>
```

## Quick Start

### Bukkit/Spigot Plugin

```java
public class MyPlugin extends JavaPlugin {
    
    // Step 1: Register dependencies in constructor
    public MyPlugin() {
        BukkitHopper.register(this, deps -> {
            deps.require(Dependency.hangar("ProtocolLib")
                .minVersion("5.0.0")
                .updatePolicy(UpdatePolicy.MINOR)
                .build());
            
            deps.require(Dependency.modrinth("packetevents")
                .version("2.11.0")
                .minecraftVersion("1.21")
                .onFailure(FailurePolicy.WARN_SKIP)  // Optional dependency
                .build());
        });
    }
    
    // Step 2: Download in onLoad (before onEnable)
    @Override
    public void onLoad() {
        DownloadResult result = BukkitHopper.download(this);
        BukkitHopper.logResult(this, result);
        
        if (result.requiresRestart()) {
            getLogger().severe("New dependencies downloaded. Please restart!");
        }
    }
    
    // Step 3: Check readiness in onEnable
    @Override
    public void onEnable() {
        if (!BukkitHopper.isReady(this)) {
            getLogger().severe("Dependencies not loaded. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Safe to use ProtocolLib and PacketEvents here
    }
}
```

### Auto-Load (No Restart Required)

Use `downloadAndLoad()` to automatically load downloaded plugins at runtime:

```java
public class MyPlugin extends JavaPlugin {

    public MyPlugin() {
        BukkitHopper.register(this, deps -> {
            deps.require(Dependency.hangar("ProtocolLib")
                .minVersion("5.0.0")
                .build());
        });
    }

    @Override
    public void onLoad() {
        // Download AND auto-load in one step
        var result = BukkitHopper.downloadAndLoad(this);

        if (result.isFullySuccessful()) {
            getLogger().info("All dependencies ready!");
        } else if (!result.loadResult().isSuccess()) {
            getLogger().warning("Some plugins couldn't be auto-loaded.");
        }
    }

    @Override
    public void onEnable() {
        // Dependencies are available immediately!
    }
}
```

**Note:** While most plugins can be hot-loaded, some plugins with complex initialization may still require a restart. The `loadResult` will indicate which plugins were successfully loaded.

### Paper Plugin (with Bootstrap)

Paper 1.19.4+ supports early loading via PluginLoader, which runs before your plugin class is even loaded.

**paper-plugin.yml:**
```yaml
name: MyPlugin
version: 1.0.0
main: com.example.myplugin.MyPlugin
loader: com.example.myplugin.MyPluginBootstrap
api-version: "1.19"
```

**MyPluginBootstrap.java:**
```java
public class MyPluginBootstrap implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder builder) {
        DownloadResult result = HopperBootstrap.create(builder.getContext())
            .require(Dependency.hangar("ProtocolLib").minVersion("5.0.0").build())
            .require(Dependency.modrinth("packetevents").version("2.11.0").build())
            .download();
        
        if (result.requiresRestart()) {
            throw new RuntimeException(
                "Dependencies downloaded: " + result.downloaded() + ". Restart required!");
        }
    }
}
```

**MyPlugin.java:**
```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Dependencies are guaranteed present!
    }
}
```

## Dependency Sources

### Hangar (PaperMC)

```java
Dependency.hangar("ProtocolLib")
    .minVersion("5.0.0")
    .minecraftVersion("1.21")  // Filter by MC version
    .build()
```

### Modrinth

```java
Dependency.modrinth("packetevents")
    .version("2.11.0")
    .minecraftVersion("1.21")
    .build()
```

### SpigotMC (via Spiget)

```java
Dependency.spiget(1997)  // ProtocolLib resource ID
    .minVersion("5.0.0")
    .build()
```

### GitHub Releases

```java
Dependency.github("dmulloy2/ProtocolLib")
    .minVersion("5.0.0")
    .assetPattern("ProtocolLib.jar")  // Match release asset
    .build()
```

### Direct URL

```java
Dependency.url("https://example.com/plugin.jar")
    .sha256("abc123...")  // Optional checksum
    .fileName("my-plugin.jar")
    .build()
```

## Version Constraints

### Exact Version

```java
.version("5.4.0")
```

### Minimum Version

```java
.minVersion("5.0.0")  // >= 5.0.0, prefer latest
```

### Version Range

```java
.versionRange(">=5.0.0 <6.0.0")  // 5.x only
```

### Latest

```java
.latest()  // Always newest
```

## Update Policies

Control how aggressively Hopper updates dependencies:

```java
.version("5.4.0")
.updatePolicy(UpdatePolicy.PATCH)  // Only 5.4.X updates
```

| Policy | Allowed Updates | Example |
|--------|-----------------|---------|
| `NONE` | Exact version only | 5.4.0 only |
| `PATCH` | Patch updates | 5.4.0 → 5.4.1, 5.4.2 |
| `MINOR` | Minor + patch | 5.4.0 → 5.5.0, 5.9.0 |
| `MAJOR` | Any newer | 5.4.0 → 6.0.0, 7.0.0 |

## Failure Policies

Handle failures per-dependency:

```java
.onFailure(FailurePolicy.WARN_SKIP)  // Optional dependency
```

| Policy | Behavior |
|--------|----------|
| `FAIL` | Throw exception (default) |
| `WARN_USE_LATEST` | Try latest available |
| `WARN_SKIP` | Skip this dependency |

## Multi-Plugin Coordination

When multiple plugins shade Hopper, they automatically coordinate:

1. **Shared Lockfile**: All plugins share `plugins/.hopper/hopper.lock`
2. **Constraint Merging**: If Plugin A needs `>=5.0.0` and Plugin B needs `>=5.2.0`, the merged constraint is `>=5.2.0`
3. **Single Download**: Dependencies are downloaded once and shared

### Conflict Resolution

If constraints are incompatible (e.g., A wants `<5.0` and B wants `>=5.0`):
- Hopper logs a warning
- Uses the higher version (may break the older plugin)

## Files Created

```
plugins/
├── .hopper/
│   ├── hopper.lock        # Resolved versions (shared)
│   ├── registry.json      # Which plugin wants what
│   └── .coordination.lock # File lock for thread safety
├── ProtocolLib-5.4.0.jar  # Downloaded dependencies
└── packetevents-2.11.0.jar
```

## Non-Standard Version Support

Hopper handles various version formats:

| Format | Example | Parsed As |
|--------|---------|-----------|
| Standard | `5.4.0` | [5, 4, 0] |
| Prefixed | `R4.0.9` | prefix="R", [4, 0, 9] |
| Snapshot | `5.4.0-SNAPSHOT` | [5, 4, 0], qualifier="SNAPSHOT" |
| Build number | `build-123` | buildNumber=123 |
| Calendar | `2024.12.20` | [2024, 12, 20] |

## API Reference

### Core Classes

- `Hopper` - Main entry point with static registration
- `Dependency` - Builder for dependencies
- `DependencyCollector` - Collects dependencies during registration
- `DownloadResult` - Result of download operation

### Version Classes

- `Version` - Flexible version parser
- `VersionConstraint` - Version constraints (exact, range, etc.)
- `UpdatePolicy` - How to handle updates

### Platform Modules

- `BukkitHopper` - Bukkit/Spigot convenience wrapper
- `BukkitHopper.DownloadAndLoadResult` - Combined download + auto-load result
- `PluginLoader` - Runtime plugin loading utility
- `HopperBootstrap` - Paper bootstrap support

## License

MIT License - see LICENSE file.
