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
		// This branch builds only unobfuscated versions, so it uses the non-remapping Loom.
		id("net.fabricmc.fabric-loom") version "1.17.19"
		// Only the dependency lookup module is used.
		// https://stonecutter.kikugie.dev/wiki/v2/reference/fletching-table/dependencies
		id("dev.kikugie.fletching-table.dependency") version "0.2.0-alpha.9"
	}
}

plugins {
	// https://stonecutter.kikugie.dev/wiki/v2/guides/start/project-setup
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Lets Gradle download the Java version each Minecraft version needs.
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		// This branch covers the unobfuscated releases (26.1+).
		// Older ranges live on the mc/<range> branches.
		versions("26.1.2", "26.2", "26.3")
		vcsVersion = "26.3"
	}
}

rootProject.name = "periscan"
