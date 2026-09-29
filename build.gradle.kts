plugins {
	id("net.fabricmc.fabric-loom")
	id("dev.kikugie.fletching-table.dependency")
	id("maven-publish")
}

// This script runs once per Minecraft version (versions/<mc>/).
// The Minecraft version is appended as build metadata, e.g. 0.3.0+26.2.
version = "${sc.properties.get<String>("mod.version")}+${sc.current.version}"
group = sc.properties.get<String>("mod.group")
base.archivesName = sc.properties.get<String>("mod.id")

val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
	else -> JavaVersion.VERSION_17
}

repositories {
	// Mod dependencies are resolved from Modrinth by Fletching Table.
	exclusiveContent {
		forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
		filter { includeGroup("maven.modrinth") }
	}
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("periscan") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets["client"])
		}
	}

	runConfigs.all {
		// Share one run directory (worlds, options) between Minecraft versions.
		runDirectory = rootProject.file("run")
	}
}

dependencies {
	/**
	 * Latest Modrinth release of [slug] for this Minecraft version, looked up by Fletching Table.
	 * https://stonecutter.kikugie.dev/wiki/v2/reference/fletching-table/dependencies
	 */
	fun modrinth(slug: String): Dependency =
		requireNotNull(fletchingTable.modrinth(slug)) { "No $slug release on Modrinth for Minecraft ${sc.current.version}" }

	minecraft("com.mojang:minecraft:${sc.current.version}")
	implementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")

	// Fabric API comes from Fabric's maven: the Modrinth jar nests its modules, so their
	// classes and access wideners would not be visible at compile time.
	implementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

	// YetAnotherConfigLib (config screens)
	implementation(modrinth("yacl"))

	// Litematica + MaLiLib: soft dependency (placement feature is disabled at runtime when absent)
	compileOnly(modrinth("litematica"))
	compileOnly(modrinth("malilib"))
	localRuntime(modrinth("litematica"))
	localRuntime(modrinth("malilib"))

	// Iris: soft dependency, compile-only (runtime would also require Sodium).
	// Used to tell Iris which shader program draws the highlight pipelines.
	compileOnly(modrinth("iris"))

	// Unit tests (JUnit)
	testImplementation(platform("org.junit:junit-bom:${sc.properties.get<String>("deps.junit")}"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// Boots Minecraft's registries in tests that need blocks/block states
	// (https://docs.fabricmc.net/develop/automatic-testing).
	testImplementation("net.fabricmc:fabric-loader-junit:${sc.properties.get<String>("deps.fabric_loader")}")
}

// The mod code lives in the client source set (splitEnvironmentSourceSets), so
// the tests compile and run against it and its Minecraft classpath.
sourceSets {
	test {
		compileClasspath += sourceSets["client"].output + sourceSets["client"].compileClasspath
		runtimeClasspath += sourceSets["client"].output + sourceSets["client"].runtimeClasspath
	}
}

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
	toolchain {
		languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
	}
}

tasks {
	test {
		useJUnitPlatform()
		// Minecraft's logging config writes logs/latest.log relative to the working
		// directory; keep it out of the project root.
		val testRunDir = layout.buildDirectory.dir("test-run").get().asFile
		workingDir = testRunDir
		doFirst { testRunDir.mkdirs() }
	}

	processResources {
		val props = mapOf(
			"version" to project.version.toString(),
			"minecraft" to sc.properties.get<String>("mod.mc_compat"),
			"java" to requiredJava.majorVersion,
			"loader" to sc.properties.get<String>("deps.fabric_loader"),
			"yacl" to sc.properties.get<String>("mod.yacl_compat"),
		)
		inputs.properties(props)
		filesMatching("fabric.mod.json") { expand(props) }
	}

	jar {
		val projectName = project.base.archivesName.get()
		inputs.property("projectName", projectName)
		from(rootProject.file("LICENSE")) {
			rename { "${it}_$projectName" }
		}
	}

	// Collects this version's jars into the root build/libs/<mod version>/.
	register<Copy>("buildAndCollect") {
		group = "build"
		description = "Builds mod jars and copies them to build/libs/{mod version}/"
		val modVersion = sc.properties.get<String>("mod.version")
		inputs.property("version", modVersion)
		from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
		into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
