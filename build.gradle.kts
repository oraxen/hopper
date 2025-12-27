plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.2" apply false
    id("maven-publish")
}

val hopperVersion: String by project

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    group = "md.thomas.hopper"
    version = hopperVersion

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "com.gradleup.shadow")

    dependencies {
        compileOnly("org.jetbrains:annotations:24.0.1")
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                from(components["java"])
            }
        }
    }
}
