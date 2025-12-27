plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.2" apply false
    id("maven-publish")
    id("org.ajoberstar.grgit.service") version "5.2.0"
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

    val publishData = PublishData(project)

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = publishData.getVersion()
                from(components["java"])
            }
        }

        repositories {
            maven {
                authentication {
                    credentials(PasswordCredentials::class) {
                        username = System.getenv("MAVEN_USERNAME")
                            ?: project.findProperty("hopperUsername") as? String ?: ""
                        password = System.getenv("MAVEN_PASSWORD")
                            ?: project.findProperty("hopperPassword") as? String ?: ""
                    }
                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
                url = uri(publishData.getRepository())
                name = "hopper"
            }
        }
    }
}

class PublishData(private val project: Project) {
    private var type: Type = getReleaseType()
    private var hashLength: Int = 7

    private fun getReleaseType(): Type {
        val ref = System.getenv("GITHUB_REF") ?: ""
        val branch = getCheckedOutBranch()
        println("Branch: $branch")
        return when {
            // Version tags (v1.0.0, v1.2.3, etc.) should publish as releases
            ref.startsWith("refs/tags/v") -> Type.RELEASE
            branch.contentEquals("master") || branch.contentEquals("main") -> Type.RELEASE
            branch.contentEquals("develop") -> Type.SNAPSHOT
            else -> Type.DEV
        }
    }

    private fun getCheckedOutGitCommitHash(): String =
        System.getenv("GITHUB_SHA")?.substring(0, hashLength) ?: "local"

    private fun getCheckedOutBranch(): String =
        System.getenv("GITHUB_REF")?.replace("refs/heads/", "")?.replace("refs/tags/", "")
            ?: try {
                org.ajoberstar.grgit.Grgit.open(mapOf("currentDir" to project.rootDir))
                    .branch.current()?.name ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }

    fun getVersion(): String = getVersion(false)

    fun getVersion(appendCommit: Boolean): String =
        type.append(getVersionString(), appendCommit, getCheckedOutGitCommitHash())

    fun getVersionString(): String =
        (project.rootProject.version as String).removeSuffix("-SNAPSHOT").removeSuffix("-DEV")

    fun getRepository(): String = type.repo

    enum class Type(private val append: String, val repo: String, private val addCommit: Boolean) {
        RELEASE("", "https://repo.oraxen.com/releases/", false),
        DEV("-DEV", "https://repo.oraxen.com/snapshots/", true),
        SNAPSHOT("-SNAPSHOT", "https://repo.oraxen.com/snapshots/", true);

        fun append(name: String, appendCommit: Boolean, commitHash: String): String =
            name.plus(append).plus(if (appendCommit && addCommit) "-".plus(commitHash) else "")
    }
}
