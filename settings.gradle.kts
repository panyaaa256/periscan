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
		// This branch covers 1.20 to 1.20.4 (Java 17).
		// Newer versions live on main and the other mc/<range> branches.
		versions("1.20.1", "1.20.4")
		vcsVersion = "1.20.4"
	}
}

rootProject.name = "periscan"
