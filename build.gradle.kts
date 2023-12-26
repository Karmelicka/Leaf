import io.papermc.paperweight.util.*

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow")
}

val log4jPlugins = sourceSets.create("log4jPlugins")
configurations.named(log4jPlugins.compileClasspathConfigurationName) {
    extendsFrom(configurations.compileClasspath.get())
}
val alsoShade: Configuration by configurations.creating

dependencies {
    // Gale start - project setup
    // Depend on own API
    implementation(project(":leaf-api")) // Leaf
    // Depend on Paper MojangAPI
    implementation("io.papermc.paper:paper-mojangapi:${project.version}") {
        exclude("io.papermc.paper", "paper-api")
    }
    // Gale end - project setup

    implementation("com.electronwill.night-config:toml:3.6.7") // Leaf - Night config

    // Leaf start - Legacy config
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.github.Carleslc.Simple-YAML:Simple-Yaml:1.8.4") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    // Leaf end

    // Paper start
    implementation("org.jline:jline-terminal-jansi:3.25.1") // Leaf - Bump Dependencies
    implementation("com.github.Dreeam-qwq:TerminalConsoleAppender:360a0759") // Leaf - Use own TerminalConsoleAppender fork to fix some issues under latest version of jline/log4j
    implementation("net.kyori:adventure-text-serializer-ansi:4.16.0") // Keep in sync with adventureVersion from Paper-API build file // Leaf - Bump Dependencies
    implementation("net.kyori:ansi:1.0.3") // Manually bump beyond above transitive dep
    /*
          Required to add the missing Log4j2Plugins.dat file from log4j-core
          which has been removed by Mojang. Without it, log4j has to classload
          all its classes to check if they are plugins.
          Scanning takes about 1-2 seconds so adding this speeds up the server start.
     */
    // Leaf start - Bump Dependencies
    implementation("org.apache.logging.log4j:log4j-core:2.23.0") // Paper - implementation
    log4jPlugins.annotationProcessorConfigurationName("org.apache.logging.log4j:log4j-core:2.23.0") // Paper - Needed to generate meta for our Log4j plugins
    runtimeOnly(log4jPlugins.output)
    alsoShade(log4jPlugins.output)
    implementation("io.netty:netty-codec-haproxy:4.1.107.Final") // Paper - Add support for proxy protocol
    // Paper end
    implementation("org.apache.logging.log4j:log4j-iostreams:2.23.0") // Paper - remove exclusion
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.spongepowered:configurate-yaml:4.2.0-SNAPSHOT") // Paper - config files
    implementation("commons-lang:commons-lang:2.6")
    implementation("net.fabricmc:mapping-io:0.5.1") // Paper - needed to read mappings for stacktrace deobfuscation
    runtimeOnly("org.xerial:sqlite-jdbc:3.45.1.0") // Leaf - Bump Dependencies
    runtimeOnly("com.mysql:mysql-connector-j:8.3.0")
    runtimeOnly("com.lmax:disruptor:3.4.4") // Paper // Leaf - Bump Dependencies - Waiting Log4j 3.x to support disruptor 4.0.0
    // Paper start - Use Velocity cipher
    implementation("com.velocitypowered:velocity-native:3.3.0-SNAPSHOT") { // Leaf - Bump Dependencies
        isTransitive = false
    }
    // Leaf end
    // Paper end - Use Velocity cipher

    runtimeOnly("org.apache.maven:maven-resolver-provider:3.9.6")
    runtimeOnly("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.18")
    runtimeOnly("org.apache.maven.resolver:maven-resolver-transport-http:1.9.18")

    // Purpur start
    implementation("org.mozilla:rhino-runtime:1.7.14")
    implementation("org.mozilla:rhino-engine:1.7.14")
    implementation("dev.omega24:upnp4j:1.0")
    // Purpur end

    // Leaf start - Bump Dependencies
    testImplementation("io.github.classgraph:classgraph:4.8.167") // Paper - mob goal test
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.ow2.asm:asm-tree:9.6")
    testImplementation("org.junit-pioneer:junit-pioneer:2.2.0") // Paper - CartesianTest

    implementation("io.netty:netty-all:4.1.107.Final")
    // Leaf end
}

val craftbukkitPackageVersion = "1_20_R3" // Paper

// Gale start - hide irrelevant compilation warnings
tasks.withType<JavaCompile> {
    val compilerArgs = options.compilerArgs
    compilerArgs.add("-Xlint:-module")
    compilerArgs.add("-Xlint:-removal")
    compilerArgs.add("-Xlint:-dep-ann")
    compilerArgs.add("--add-modules=jdk.incubator.vector") // Gale - Pufferfish - SIMD support
}
// Gale end - hide irrelevant compilation warnings

tasks.jar {
    archiveClassifier.set("dev")

    manifest {
        val git = Git(rootProject.layout.projectDirectory.path)
        val gitHash = git("rev-parse", "--short=7", "HEAD").getText().trim()
        val implementationVersion = System.getenv("BUILD_NUMBER") ?: "\"$gitHash\""
        val date = git("show", "-s", "--format=%ci", gitHash).getText().trim() // Paper
        val gitBranch = git("rev-parse", "--abbrev-ref", "HEAD").getText().trim() // Paper
        attributes(
            "Main-Class" to "org.bukkit.craftbukkit.Main",
            "Implementation-Title" to "CraftBukkit",
            "Implementation-Version" to "git-Leaf-$implementationVersion", // Gale - branding changes // Leaf
            "Implementation-Vendor" to date, // Paper
            "Specification-Title" to "Bukkit",
            "Specification-Version" to project.version,
            "Specification-Vendor" to "Bukkit Team",
            "Git-Branch" to gitBranch, // Paper
            "Git-Commit" to gitHash, // Paper
            "CraftBukkit-Package-Version" to craftbukkitPackageVersion, // Paper
        )
        for (tld in setOf("net", "com", "org")) {
            attributes("$tld/bukkit", "Sealed" to true)
        }
    }
}

tasks.compileJava {
    // incremental compilation is currently broken due to patched files having compiled counterparts already on the compile classpath
    options.setIncremental(false)
}

// Paper start - compile tests with -parameters for better junit parameterized test names
tasks.compileTestJava {
    options.compilerArgs.add("-parameters")
}
// Paper end

publishing {
    publications.create<MavenPublication>("maven") {
        artifact(tasks.shadowJar)
    }
}

relocation {
    // Order matters here - e.g. craftbukkit proper must be relocated before any of the libs are relocated into the cb package
    relocate("org.bukkit.craftbukkit" to "org.bukkit.craftbukkit.v$craftbukkitPackageVersion") {
        exclude("org.bukkit.craftbukkit.Main*")
    }
}

tasks.shadowJar {
    configurations = listOf(project.configurations.vanillaServer.get(), alsoShade)
    archiveClassifier.set("mojang-mapped")

    for (relocation in relocation.relocations.get()) {
        relocate(relocation.fromPackage, relocation.toPackage) {
            for (exclude in relocation.excludes) {
                exclude(exclude)
            }
        }
    }
}

// Paper start
val scanJar = tasks.register("scanJarForBadCalls", io.papermc.paperweight.tasks.ScanJarForBadCalls::class) {
    badAnnotations.add("Lio/papermc/paper/annotation/DoNotUse;")
    jarToScan.set(tasks.shadowJar.flatMap { it.archiveFile })
    classpath.from(configurations.compileClasspath)
}
tasks.check {
    dependsOn(scanJar)
}
// Paper end

// Paper start - include reobf mappings in jar for stacktrace deobfuscation
val includeMappings = tasks.register<io.papermc.paperweight.tasks.IncludeMappings>("includeMappings") {
    inputJar.set(tasks.fixJarForReobf.flatMap { it.outputJar })
    mappings.set(tasks.reobfJar.flatMap { it.mappingsFile })
    mappingsDest.set("META-INF/mappings/reobf.tiny")
}

tasks.reobfJar {
    inputJar.set(includeMappings.flatMap { it.outputJar })
}
// Paper end - include reobf mappings in jar for stacktrace deobfuscation

tasks.test {
    exclude("org/bukkit/craftbukkit/inventory/ItemStack*Test.class")
    useJUnitPlatform()
}

fun TaskContainer.registerRunTask(
    name: String,
    block: JavaExec.() -> Unit
): TaskProvider<JavaExec> = register<JavaExec>(name) {
    group = "paperweight" // Purpur
    mainClass.set("org.bukkit.craftbukkit.Main")
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory
        .dir(providers.gradleProperty("paper.runWorkDir").getOrElse("run"))
        .asFile
    javaLauncher.set(project.javaToolchains.defaultJavaLauncher(project))

    if (rootProject.childProjects["test-plugin"] != null) {
        val testPluginJar = rootProject.project(":test-plugin").tasks.jar.flatMap { it.archiveFile }
        inputs.file(testPluginJar)
        args("-add-plugin=${testPluginJar.get().asFile.absolutePath}")
    }

    args("--nogui")
    systemProperty("net.kyori.adventure.text.warnWhenLegacyFormattingDetected", true)
    if (providers.gradleProperty("paper.runDisableWatchdog").getOrElse("false") == "true") {
        systemProperty("disable.watchdog", true)
    }
    systemProperty("io.papermc.paper.suppress.sout.nags", true)

    val memoryGb = providers.gradleProperty("paper.runMemoryGb").getOrElse("2")
    minHeapSize = "${memoryGb}G"
    maxHeapSize = "${memoryGb}G"
    jvmArgs("--enable-preview") // Gale - enable preview features for development runs
    jvmArgs("--add-modules=jdk.incubator.vector") // Gale - Pufferfish - SIMD support

    doFirst {
        workingDir.mkdirs()
    }

    block(this)
}

val runtimeClasspathWithoutVanillaServer = configurations.runtimeClasspath.flatMap { it.elements }
    .zip(configurations.vanillaServer.map { it.singleFile.absolutePath }) { runtime, vanilla ->
        runtime.filterNot { it.asFile.absolutePath == vanilla }
    }

tasks.registerRunTask("runShadow") {
    description = "Spin up a test server from the shadowJar archiveFile"
    classpath(tasks.shadowJar.flatMap { it.archiveFile })
    classpath(runtimeClasspathWithoutVanillaServer)
}

tasks.registerRunTask("runReobf") {
    description = "Spin up a test server from the reobfJar output jar"
    classpath(tasks.reobfJar.flatMap { it.outputJar })
    classpath(runtimeClasspathWithoutVanillaServer)
}

val runtimeClasspathForRunDev = sourceSets.main.flatMap { src ->
    src.runtimeClasspath.elements.map { elements ->
        elements.filterNot { file -> file.asFile.endsWith("minecraft.jar") }
    }
}
tasks.registerRunTask("runDev") {
    description = "Spin up a non-relocated Mojang-mapped test server"
    classpath(tasks.filterProjectDir.flatMap { it.outputJar })
    classpath(runtimeClasspathForRunDev)
    jvmArgs("-DPaper.isRunDev=true")
}

// Gale start - package license into jar
tasks.register<Copy>("copyLicense") {
    from(layout.projectDirectory.file("LICENSE.txt"))
    into(layout.buildDirectory.dir("tmp/copiedlicense"))
}

tasks.processResources {
    dependsOn("copyLicense")
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("tmp/copiedlicense"))
        }
    }
}
// Gale end - package license into jar

repositories {
    mavenCentral()
}
