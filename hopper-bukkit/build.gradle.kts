plugins {
    id("java")
    id("java-library")
    id("com.gradleup.shadow")
}

dependencies {
    api(project(":hopper-core"))
    
    // Bukkit/Spigot API (compileOnly - provided by server)
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveClassifier.set("")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveBaseName.set("hopper-bukkit")
}
