import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask

plugins {
    id("fabric-loom") version "0.11-SNAPSHOT"
    scala
    `maven-publish`
}

val archives_base_name: String by project

base {
    archivesName.set(archives_base_name)
}

val mod_version: String by project
val maven_group: String by project

version = mod_version
group = maven_group

sourceSets {
    val main = main.get()
    create("gametest") {
        compileClasspath += main.compileClasspath
        compileClasspath += main.output
        runtimeClasspath +=main.runtimeClasspath
        runtimeClasspath += main.output
    }
}
val gameTestSource = sourceSets.getByName("gametest")

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven("https://s01.oss.sonatype.org/service/local/repositories/snapshots/content/")
    maven("https://maven.turtton.net")
    mavenCentral()
}

val minecraft_version: String by project
val yarn_mappings: String by project
val loader_version: String by project
val fabric_version: String by project

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${loader_version}")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")
    modImplementation("net.fabricmc:fabric-language-scala:0.3.1.+")

    modImplementation("net.turtton:weaver:0.1.4")

    implementS2MC("s2mc-client-core_3")
    implementS2MC("s2mc-client-impl_3")
    implementS2MC("s2mc-protocol-core_3")
    implementS2MC("s2mc-protocol-impl_3")

    implementation("com.comcast:ip4s-core_3:3.1.2")
    implementation("co.fs2:fs2-core_3:3.2.4")
    implementation("dev.optics:monocle-core_3:3.1.0")
    implementation("dev.optics:monocle-macro_3:3.1.0")
//    implementation("org.typelevel:cats-core_3:2.6.1")
//    implementation("org.typelevel:cats-effect_3:3.2.8")
//    implementation("org.eclipse.jetty:jetty-servlet:11.0.6")


    // PSA: Some older mods, compiled on Loom 0.2.1, might have outdated Maven POMs.
    // You may need to force-disable transitiveness on them.
//    testImplementation("org.scalatest:scalatest_3:3.2.10")
//    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

fun DependencyHandlerScope.implementS2MC(name: String) {
    implementation(
        group = "io.github.kory33",
        name = name,
        version = "0.2.4-SNAPSHOT"
    )
}

loom {
    runs {
        create("gameTest") {
            server()
            configName = "GameTest"
            vmArgs += "-Dfabric-api.gametest"
            vmArgs += "-Dfabric.api.gametest.report-file=${project.buildDir}/junit.xml"
            runDir = "gametest"
            setSource(gameTestSource)
            isIdeConfigGenerated = true
        }
    }
}

tasks.getByName<ProcessResources>("processResources") {
    //    inputs.property("version"), project.version
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val targetJavaVersion = 16
tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
//    it.options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}


java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

tasks.withType<ScalaCompile> {
    scalaCompileOptions.apply {
        additionalParameters = listOf(
            "-Yretain-trees",
            "-Xcheck-macros",
            "-Ykind-projector:underscores"
        )
    }
}

tasks.getByName<Jar>("jar") {
    from("LICENSE").also {
        it.rename { license -> "${license}_${archives_base_name}" }
    }
}

val remapJar = tasks.getByName<RemapJarTask>("remapJar")
val sourcesJar = tasks.getByName<Jar>("sourcesJar")
val remapSourcesJar = tasks.getByName<RemapSourcesJarTask>("remapSourcesJar")

// configure the maven publication
publishing {
    publications {
        create("mavenJava", MavenPublication::class.java) {
            // add all the jars that should be included when publishing to maven
            artifact(remapJar) {
                builtBy(remapJar)
            }
            artifact(sourcesJar) {
                builtBy(remapSourcesJar)
            }
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}

tasks.getByName<Test>("test").dependsOn("runGameTest")
