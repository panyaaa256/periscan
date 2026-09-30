package com.panyaaa256.periscan.persist;

import com.panyaaa256.periscan.persist.ProfileFile.Data;
import com.panyaaa256.periscan.persist.ProfileFile.SavedProfile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileFileTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
	private static final SavedProfile OW = new SavedProfile(0, 0, 0, 0, "minecraft:overworld", "2026-01-01");
	private static final SavedProfile NETHER = new SavedProfile(-3, -2, 4, 5, "minecraft:the_nether", "2026-02-03");

	@TempDir
	Path dir;

	private Path file() {
		return dir.resolve("worlds").resolve("sp_test.json");
	}

	private Path writeRaw(String json) throws IOException {
		Path file = file();
		Files.createDirectories(file.getParent());
		Files.writeString(file, json);
		return file;
	}

	@Nested
	class ReadWrite {
		@Test
		void missingFileIsEmptyAndNotCreated() {
			Data data = ProfileFile.read(file(), TODAY);
			assertTrue(data.profiles.isEmpty());
			assertNull(data.lastScanned);
			assertTrue(data.writable);
			assertFalse(Files.exists(file()));
		}

		@Test
		void roundTripKeepsProfilesOrderAndLastScanned() {
			Data data = new Data();
			data.profiles.put("b", NETHER);
			data.profiles.put("a", OW);
			data.lastScanned = "a";
			ProfileFile.write(file(), data);

			Data read = ProfileFile.read(file(), TODAY);
			assertEquals(List.of("b", "a"), List.copyOf(read.profiles.keySet()));
			assertEquals(NETHER, read.profiles.get("b"));
			assertEquals(OW, read.profiles.get("a"));
			assertEquals("a", read.lastScanned);
		}

		@Test
		void lastScannedMissingOrNullIsNull() throws IOException {
			writeRaw("{\"profiles\":{}}");
			assertNull(ProfileFile.read(file(), TODAY).lastScanned);
			writeRaw("{\"profiles\":{},\"lastScanned\":null}");
			assertNull(ProfileFile.read(file(), TODAY).lastScanned);
		}

		@Test
		void writeReplacesTheFileAndLeavesNoTemporaryFile() throws IOException {
			writeRaw("old contents");
			Data data = new Data();
			data.profiles.put("ow", OW);
			ProfileFile.write(file(), data);

			assertEquals(OW, ProfileFile.read(file(), TODAY).profiles.get("ow"));
			try (var files = Files.list(file().getParent())) {
				assertEquals(List.of(file()), files.toList());
			}
		}

		@Test
		void nonWritableDataIsNeverWritten() throws IOException {
			Path file = writeRaw("original");
			Data data = new Data();
			data.writable = false;
			ProfileFile.write(file, data);
			assertEquals("original", Files.readString(file));
		}
	}

	@Nested
	class InvalidEntries {
		private static final String VALID = "{\"minX\":0,\"minZ\":0,\"maxX\":1,\"maxZ\":1,"
				+ "\"dimension\":\"minecraft:overworld\",\"createdAt\":\"2026-01-01\"}";

		private Data readWithProfile(String invalid) throws IOException {
			writeRaw("{\"profiles\":{\"ok\":" + VALID + ",\"bad\":" + invalid + "}}");
			return ProfileFile.read(file(), TODAY);
		}

		@Test
		void invalidEntriesAreSkippedAndTheRestIsKept() throws IOException {
			String[] invalid = {
					"null",
					"\"text\"",
					"{\"minX\":0,\"minZ\":0,\"maxX\":1,\"maxZ\":1}",
					"{\"minX\":0,\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":null}",
					"{\"minX\":0,\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":3}",
					"{\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":\"minecraft:overworld\"}",
					"{\"minX\":\"0\",\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":\"minecraft:overworld\"}",
					"{\"minX\":1.5,\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":\"minecraft:overworld\"}",
					"{\"minX\":3000000000,\"minZ\":0,\"maxX\":1,\"maxZ\":1,\"dimension\":\"minecraft:overworld\"}",
			};
			for (String entry : invalid) {
				Data data = readWithProfile(entry);
				assertEquals(List.of("ok"), List.copyOf(data.profiles.keySet()), entry);
				assertTrue(Files.exists(file()), "a skipped entry must not mark the file broken: " + entry);
			}
		}

		@Test
		void missingCreatedAtIsAllowed() throws IOException {
			Data data = readWithProfile("{\"minX\":-1,\"minZ\":2,\"maxX\":3,\"maxZ\":4,\"dimension\":\"minecraft:the_nether\"}");
			assertEquals(new SavedProfile(-1, 2, 3, 4, "minecraft:the_nether", null), data.profiles.get("bad"));
		}

		@Test
		void wholeNumbersWrittenAsDecimalsAreAccepted() throws IOException {
			Data data = readWithProfile("{\"minX\":2.0,\"minZ\":0,\"maxX\":3,\"maxZ\":4,\"dimension\":\"minecraft:overworld\"}");
			assertEquals(2, data.profiles.get("bad").minX());
		}
	}

	@Nested
	class Legacy {
		@Test
		void oldestSingleRegionBecomesOverworldProfile() throws IOException {
			writeRaw("{\"x1\":3,\"z1\":-2,\"x2\":-1,\"z2\":5}");
			Data data = ProfileFile.read(file(), TODAY);
			assertEquals(new SavedProfile(-1, -2, 3, 5, "minecraft:overworld", "2026-09-27"),
					data.profiles.get("overworld"));
			assertEquals("overworld", data.lastScanned);
		}

		@Test
		void perDimensionRegionsAreNamedAfterTheDimensionPath() throws IOException {
			writeRaw("{\"minecraft:overworld\":{\"x1\":0,\"z1\":0,\"x2\":1,\"z2\":1},"
					+ "\"minecraft:the_nether\":{\"x1\":5,\"z1\":5,\"x2\":4,\"z2\":4}}");
			Data data = ProfileFile.read(file(), TODAY);
			assertEquals(List.of("overworld", "the_nether"), List.copyOf(data.profiles.keySet()));
			assertEquals(new SavedProfile(4, 4, 5, 5, "minecraft:the_nether", "2026-09-27"),
					data.profiles.get("the_nether"));
			// The first migrated profile becomes the last-scanned one.
			assertEquals("overworld", data.lastScanned);
		}

		@Test
		void migratedFileIsWrittenBackInTheNewFormat() throws IOException {
			writeRaw("{\"x1\":0,\"z1\":0,\"x2\":0,\"z2\":0}");
			ProfileFile.read(file(), TODAY);
			assertTrue(Files.readString(file()).contains("\"profiles\""));
			// A second read is a plain read of the new format.
			assertEquals("overworld", ProfileFile.read(file(), LocalDate.of(2030, 1, 1)).lastScanned);
			assertEquals("2026-09-27", ProfileFile.read(file(), TODAY).profiles.get("overworld").createdAt());
		}
	}

	@Nested
	class BrokenFile {
		@Test
		void brokenJsonIsMovedAsideAndReadAsEmpty() throws IOException {
			writeRaw("{not json");
			Data data = ProfileFile.read(file(), TODAY);
			assertTrue(data.profiles.isEmpty());
			assertTrue(data.writable);
			assertFalse(Files.exists(file()));
			assertEquals("{not json", Files.readString(dir.resolve("worlds/sp_test.json.broken")));
		}

		@Test
		void nonObjectJsonCountsAsBroken() throws IOException {
			writeRaw("[]");
			ProfileFile.read(file(), TODAY);
			assertTrue(Files.exists(dir.resolve("worlds/sp_test.json.broken")));
		}

		@Test
		void savingAfterRecoveryKeepsTheBackup() throws IOException {
			writeRaw("{not json");
			Data data = ProfileFile.read(file(), TODAY);
			data.profiles.put("new", OW);
			ProfileFile.write(file(), data);
			assertEquals("{not json", Files.readString(dir.resolve("worlds/sp_test.json.broken")));
			assertEquals(OW, ProfileFile.read(file(), TODAY).profiles.get("new"));
		}

		@Test
		void whenTheBackupFailsNothingIsOverwritten() throws IOException {
			writeRaw("{not json");
			// A non-empty directory where the backup should go makes the move fail.
			Files.createDirectories(dir.resolve("worlds/sp_test.json.broken/blocker"));
			Data data = ProfileFile.read(file(), TODAY);
			assertFalse(data.writable);
			data.profiles.put("new", OW);
			ProfileFile.write(file(), data);
			assertEquals("{not json", Files.readString(file()));
		}
	}

	@Nested
	class DataOps {
		@Test
		void removeClearsTheLastScannedMarkerOnlyForThatProfile() {
			Data data = new Data();
			data.profiles.put("a", OW);
			data.profiles.put("b", NETHER);
			data.lastScanned = "a";
			assertTrue(data.remove("b"));
			assertEquals("a", data.lastScanned);
			assertTrue(data.remove("a"));
			assertNull(data.lastScanned);
			assertFalse(data.remove("a"));
		}

		@Test
		void lastScannedProfileMustStillExist() {
			Data data = new Data();
			assertFalse(data.hasLastScannedProfile());
			data.lastScanned = "gone";
			assertFalse(data.hasLastScannedProfile());
			data.profiles.put("gone", OW);
			assertTrue(data.hasLastScannedProfile());
		}

		@Test
		void savedProfileRoundTripsThroughPeriProfile() {
			assertEquals(NETHER, SavedProfile.of(NETHER.toProfile("n")));
			assertEquals("n", NETHER.toProfile("n").name());
		}
	}

	@Nested
	class FormatVersion {
		@Test
		void writtenFilesCarryTheFormatVersion() throws IOException {
			ProfileFile.write(file(), new Data());
			assertTrue(Files.readString(file()).contains("\"version\": " + ProfileFile.FORMAT_VERSION));
		}

		@Test
		void filesWithoutAVersionAreReadAsTheCurrentFormat() throws IOException {
			writeRaw("{\"profiles\":{\"ow\":{\"minX\":0,\"minZ\":0,\"maxX\":0,\"maxZ\":0,"
					+ "\"dimension\":\"minecraft:overworld\",\"createdAt\":\"2026-01-01\"}}}");
			Data data = ProfileFile.read(file(), TODAY);
			assertEquals(OW, data.profiles.get("ow"));
			assertTrue(data.writable);
		}

		@Test
		void filesOfANewerFormatAreReadButNeverOverwritten() throws IOException {
			String json = "{\"version\":" + (ProfileFile.FORMAT_VERSION + 1) + ",\"profiles\":{\"ow\":{\"minX\":0,"
					+ "\"minZ\":0,\"maxX\":0,\"maxZ\":0,\"dimension\":\"minecraft:overworld\",\"createdAt\":\"2026-01-01\"}}}";
			Path file = writeRaw(json);
			Data data = ProfileFile.read(file, TODAY);
			assertEquals(OW, data.profiles.get("ow"));
			assertFalse(data.writable);

			ProfileFile.write(file, data);
			assertEquals(json, Files.readString(file));
		}
	}

	@Nested
	class FileNames {
		@Test
		void legacyNamesAreSanitizedAndCanCollide() {
			assertEquals("sp_My_World__1_2", ProfileFile.legacyFileName("sp_", "My World: 1/2"));
			assertEquals("mp_play.example.com_25565", ProfileFile.legacyFileName("mp_", "play.example.com:25565"));
			assertEquals("sp____", ProfileFile.legacyFileName("sp_", "日本語"));
		}

		@Test
		void namesThatSanitizeAlikeGetDifferentFiles() {
			// All three sanitize to "_____".
			String survival = ProfileFile.fileName("sp_", "サバイバル");
			String creative = ProfileFile.fileName("sp_", "クリエイト");
			String newWorld = ProfileFile.fileName("sp_", "新しい世界");
			assertEquals(3, Set.of(survival, creative, newWorld).size());
			assertTrue(survival.startsWith("sp______-"));
		}

		@Test
		void fileNameKeepsTheReadablePartAndIsStable() {
			String name = ProfileFile.fileName("mp_", "play.example.com:25565");
			assertTrue(name.matches("mp_play\\.example\\.com_25565-[0-9a-f]{8}"), name);
			assertEquals(name, ProfileFile.fileName("mp_", "play.example.com:25565"));
		}

		@Test
		void longNamesAreShortened() {
			String name = ProfileFile.fileName("sp_", "x".repeat(300));
			assertEquals("sp_".length() + 48 + "-".length() + 8, name.length());
		}
	}

	@Nested
	class LegacyFileCopy {
		private Path legacy() {
			return dir.resolve("worlds").resolve("sp_old.json");
		}

		@Test
		void missingFileIsFilledFromTheLegacyFile() {
			Data old = new Data();
			old.profiles.put("ow", OW);
			old.lastScanned = "ow";
			ProfileFile.write(legacy(), old);

			Data read = ProfileFile.read(file(), legacy(), TODAY);
			assertEquals(OW, read.profiles.get("ow"));
			assertEquals("ow", read.lastScanned);
			assertTrue(Files.exists(file()));
			// Other worlds that shared the legacy file may still need it.
			assertTrue(Files.exists(legacy()));
		}

		@Test
		void existingFileIgnoresTheLegacyFile() {
			Data old = new Data();
			old.profiles.put("ow", OW);
			ProfileFile.write(legacy(), old);
			Data current = new Data();
			current.profiles.put("nether", NETHER);
			ProfileFile.write(file(), current);

			Data read = ProfileFile.read(file(), legacy(), TODAY);
			assertEquals(List.of("nether"), List.copyOf(read.profiles.keySet()));
		}

		@Test
		void emptyLegacyDataIsNotCopied() {
			ProfileFile.write(legacy(), new Data());
			ProfileFile.read(file(), legacy(), TODAY);
			assertFalse(Files.exists(file()));
		}
	}
}
