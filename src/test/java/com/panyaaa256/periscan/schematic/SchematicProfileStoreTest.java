package com.panyaaa256.periscan.schematic;

import com.panyaaa256.periscan.schematic.SchematicProfileStore.UpdateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicProfileStoreTest {
	private static final SchematicEntry BASE = new SchematicEntry("base.litematic", EnumSet.of(Corner.MM), -59, false);
	private static final SchematicEntry WALL = new SchematicEntry("wall.litematic", EnumSet.of(Corner.PM, Corner.MP), 5, true);

	@TempDir
	Path root;
	@TempDir
	Path sources;

	private Path source(String fileName, String content) throws IOException {
		Path file = sources.resolve(fileName);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
		return file;
	}

	private static SchematicProfile profile(SchematicEntry... entries) {
		return new SchematicProfile(SchematicEntry.DEFAULT_ORIGIN_Y, List.of(entries));
	}

	private Path writeRaw(String name, String json) throws IOException {
		Path file = root.resolve(name).resolve(SchematicProfileStore.PROFILE_FILE);
		Files.createDirectories(file.getParent());
		Files.writeString(file, json);
		return file;
	}

	@Nested
	class Entry {
		@Test
		void cornersCompareEqualHoweverTheyWereBuilt() {
			assertEquals(new SchematicEntry("a.litematic", EnumSet.of(Corner.PP, Corner.MM), 0, false),
					new SchematicEntry("a.litematic", Set.of(Corner.MM, Corner.PP), 0, false));
		}

		@Test
		void originYIsClamped() {
			assertEquals(SchematicEntry.MAX_ORIGIN_Y, new SchematicEntry("a.litematic", EnumSet.noneOf(Corner.class), 9999, false).originY());
			assertEquals(SchematicEntry.MIN_ORIGIN_Y, new SchematicEntry("a.litematic", EnumSet.noneOf(Corner.class), -9999, false).originY());
		}

		@Test
		void fileNamesWithPathsAreInvalid() {
			assertTrue(SchematicEntry.isValidFileName("a.litematic"));
			assertFalse(SchematicEntry.isValidFileName("../a.litematic"));
			assertFalse(SchematicEntry.isValidFileName("sub\\a.litematic"));
			assertFalse(SchematicEntry.isValidFileName("a.txt"));
			assertFalse(SchematicEntry.isValidFileName(".litematic"));
		}
	}

	@Nested
	class LoadAndNames {
		@Test
		void missingProfileIsEmptyAndNotCreated() {
			assertEquals(List.of(), SchematicProfileStore.load(root, "ow").entries());
			assertFalse(SchematicProfileStore.exists(root, "ow"));
			assertFalse(Files.exists(root.resolve("ow")));
		}

		@Test
		void roundTripKeepsEntriesAndOrder() {
			SchematicProfileStore.update(root, "ow", profile(WALL, BASE), List.of());
			assertEquals(List.of(WALL, BASE), SchematicProfileStore.load(root, "ow").entries());
		}

		@Test
		void invalidEntriesAreSkipped() throws IOException {
			writeRaw("ow", """
					{"version":1,"schematics":[
					{"file":"../evil.litematic","corners":["--"],"originY":0},
					{"file":"ok.litematic","corners":["--","??","++"],"originY":7,"keepOrientation":true},
					{"file":"ok.litematic","corners":["+-"],"originY":1},
					{"corners":["--"]},
					{"file":"noy.litematic","corners":[]}
					]}""");
			assertEquals(List.of(
					new SchematicEntry("ok.litematic", EnumSet.of(Corner.MM, Corner.PP), 7, true),
					new SchematicEntry("noy.litematic", EnumSet.noneOf(Corner.class), SchematicEntry.DEFAULT_ORIGIN_Y, false)),
					SchematicProfileStore.load(root, "ow").entries());
		}

		@Test
		void brokenFileIsMovedAside() throws IOException {
			Path file = writeRaw("ow", "{not json");
			assertEquals(List.of(), SchematicProfileStore.load(root, "ow").entries());
			assertFalse(Files.exists(file));
			assertTrue(Files.exists(file.resolveSibling(SchematicProfileStore.PROFILE_FILE + ".broken")));
		}

		@Test
		void namesAreSortedFoldersWithAProfileFile() throws IOException {
			SchematicProfileStore.update(root, "nether", profile(), List.of());
			SchematicProfileStore.update(root, "ow", profile(), List.of());
			Files.createDirectories(root.resolve("empty"));
			assertEquals(List.of("nether", "ow"), SchematicProfileStore.names(root));
		}

		@Test
		void namesOfMissingRootAreEmpty() {
			assertEquals(List.of(), SchematicProfileStore.names(root.resolve("missing")));
		}

		@Test
		void dotNamesAreInvalid() {
			assertTrue(SchematicProfileStore.isValidName("ow-2.0"));
			assertFalse(SchematicProfileStore.isValidName(".."));
			assertFalse(SchematicProfileStore.isValidName("."));
			assertFalse(SchematicProfileStore.isValidName(""));
		}
	}

	@Nested
	class Update {
		@Test
		void importCopiesTheFileAndAddsADefaultEntry() throws IOException {
			Path source = source("sub/base.litematic", "v1");
			UpdateResult result = SchematicProfileStore.update(root, "ow", profile(WALL), List.of(source));
			assertEquals(List.of(WALL, SchematicEntry.imported("base.litematic", SchematicEntry.DEFAULT_ORIGIN_Y)), result.profile().entries());
			assertEquals(List.of(), result.failedImports());
			assertEquals("v1", Files.readString(root.resolve("ow").resolve("base.litematic")));
			assertEquals(result.profile().entries(), SchematicProfileStore.load(root, "ow").entries());
			// The original stays where it was.
			assertTrue(Files.exists(source));
		}

		@Test
		void reimportReplacesTheCopyAndKeepsTheEntry() throws IOException {
			SchematicProfileStore.update(root, "ow", profile(), List.of(source("wall.litematic", "v1")));
			UpdateResult result = SchematicProfileStore.update(root, "ow", profile(WALL),
					List.of(source("wall.litematic", "v2")));
			assertEquals(List.of(WALL), result.profile().entries());
			assertEquals("v2", Files.readString(root.resolve("ow").resolve("wall.litematic")));
		}

		@Test
		void droppedEntriesLoseTheirCopy() throws IOException {
			SchematicProfileStore.update(root, "ow", profile(),
					List.of(source("base.litematic", "b"), source("wall.litematic", "w")));
			SchematicProfileStore.update(root, "ow", profile(WALL), List.of());
			assertFalse(Files.exists(root.resolve("ow").resolve("base.litematic")));
			assertTrue(Files.exists(root.resolve("ow").resolve("wall.litematic")));
			assertEquals(List.of(WALL), SchematicProfileStore.load(root, "ow").entries());
		}

		@Test
		void failedImportsAreReportedAndNotListed() throws IOException {
			Path missing = sources.resolve("missing.litematic");
			Path notSchematic = source("notes.txt", "x");
			UpdateResult result = SchematicProfileStore.update(root, "ow", profile(), List.of(missing, notSchematic));
			assertEquals(List.of(), result.profile().entries());
			assertEquals(List.of(missing, notSchematic), result.failedImports());
			// The profile is still created.
			assertTrue(SchematicProfileStore.exists(root, "ow"));
		}
	}

	@Nested
	class DefaultOriginY {
		@Test
		void isSavedAndGivenToImportedSchematics() throws IOException {
			UpdateResult result = SchematicProfileStore.update(root, "nether",
					new SchematicProfile(5, List.of(WALL)), List.of(source("base.litematic", "b")));
			assertEquals(List.of(WALL, SchematicEntry.imported("base.litematic", 5)), result.profile().entries());
			assertEquals(new SchematicProfile(5, result.profile().entries()), SchematicProfileStore.load(root, "nether"));
		}

		@Test
		void missingInTheFileIsTheBuiltInDefault() throws IOException {
			writeRaw("ow", "{\"version\":1,\"schematics\":[]}");
			assertEquals(SchematicProfile.EMPTY, SchematicProfileStore.load(root, "ow"));
		}
	}

	@Nested
	class SameNamedImports {
		@Test
		void allFailAndNothingIsCopied() throws IOException {
			Path a = source("a/wall.litematic", "a");
			Path b = source("b/wall.litematic", "b");
			Path base = source("base.litematic", "x");
			UpdateResult result = SchematicProfileStore.update(root, "ow", profile(), List.of(a, base, b));
			assertEquals(List.of(a, b), result.failedImports());
			assertEquals(List.of(SchematicEntry.imported("base.litematic", SchematicEntry.DEFAULT_ORIGIN_Y)),
					result.profile().entries());
			assertFalse(Files.exists(root.resolve("ow").resolve("wall.litematic")));
		}
	}

	@Nested
	class Copy {
		@Test
		void copiesSettingsAndFiles() throws IOException {
			SchematicProfileStore.update(root, "ow", new SchematicProfile(7, List.of()),
					List.of(source("wall.litematic", "w")));
			SchematicProfileStore.update(root, "ow", new SchematicProfile(7, List.of(WALL)), List.of());

			UpdateResult result = SchematicProfileStore.copy(root, "ow", "nether");
			assertEquals(List.of(), result.failedImports());
			assertEquals(new SchematicProfile(7, List.of(WALL)), SchematicProfileStore.load(root, "nether"));
			assertEquals("w", Files.readString(root.resolve("nether").resolve("wall.litematic")));
			// The source is untouched.
			assertEquals(new SchematicProfile(7, List.of(WALL)), SchematicProfileStore.load(root, "ow"));
			assertTrue(Files.exists(root.resolve("ow").resolve("wall.litematic")));
		}

		@Test
		void missingFilesAreReported() {
			SchematicProfileStore.update(root, "ow", profile(WALL), List.of());
			UpdateResult result = SchematicProfileStore.copy(root, "ow", "nether");
			assertEquals(List.of(root.resolve("ow").resolve("wall.litematic")), result.failedImports());
			assertEquals(List.of(WALL), SchematicProfileStore.load(root, "nether").entries());
		}
	}

	@Nested
	class Remove {
		@Test
		void deletesTheFolderWithItsCopies() throws IOException {
			SchematicProfileStore.update(root, "ow", profile(), List.of(source("base.litematic", "b")));
			assertTrue(SchematicProfileStore.remove(root, "ow"));
			assertFalse(Files.exists(root.resolve("ow")));
			assertEquals(List.of(), SchematicProfileStore.names(root));
		}

		@Test
		void missingProfileIsNotRemoved() {
			assertFalse(SchematicProfileStore.remove(root, "ow"));
		}
	}
}
