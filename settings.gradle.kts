pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		// Fletching Table is only published to the snapshots repository.
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		mavenCentral()
		gradlePluginPortal()
	}
	plugins {
		// Only the dependency lookup module is used.
		// https://stonecutter.kikugie.dev/wiki/v2/reference/fletching-table/dependencies
		id("dev.kikugie.fletching-table.dependency") version "0.2.0-alpha.9"
	}
}

plugins {
	// https://stonecutter.kikugie.dev/wiki/v2/guides/start/project-setup
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Picks the remapping Loom for obfuscated versions (<26.1) and the plain one
	// for unobfuscated versions, and aliases the mod* configurations and jar tasks.
	// https://stonecutter.kikugie.dev/wiki/v2/guides/tips/loom-back-compat
	id("dev.kikugie.loom-back-compat") version "0.4.2"
	// Lets Gradle download the Java version each Minecraft version needs.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		// This branch covers 1.20.5 to 1.21.4 (Java 21, before RenderPipelines).
		// Newer versions live on main and mc/1.21.5-1.21.8, older ones on
		// mc/1.19.4-1.20.4.
		versions("1.20.6", "1.21.1", "1.21.3", "1.21.4")
		vcsVersion = "1.21.4"
	}
}

rootProject.name = "periscan"
