package com.panyaaa256.periscan.persist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeFilesTest {
	@TempDir
	Path dir;

	@Test
	void writeCreatesMissingFoldersAndLeavesNoTemporaryFile() throws IOException {
		Path file = dir.resolve("a").resolve("b.json");
		SafeFiles.writeAtomically(file, "one");
		assertEquals("one", Files.readString(file));
		assertFalse(Files.exists(dir.resolve("a").resolve("b.json.tmp")));
	}

	@Test
	void writeReplacesAnExistingFile() throws IOException {
		Path file = dir.resolve("b.json");
		SafeFiles.writeAtomically(file, "one");
		SafeFiles.writeAtomically(file, "two");
		assertEquals("two", Files.readString(file));
	}

	@Test
	void writeFailsWhenTheParentIsAFile() throws IOException {
		Path blocker = dir.resolve("blocker");
		Files.writeString(blocker, "x");
		assertThrows(IOException.class, () -> SafeFiles.writeAtomically(blocker.resolve("b.json"), "one"));
	}

	@Test
	void moveAsideKeepsTheContentAsDotBrokenAndReplacesAnOlderOne() throws IOException {
		Path file = dir.resolve("b.json");
		Files.writeString(dir.resolve("b.json.broken"), "older");
		Files.writeString(file, "garbage");
		Path backup = SafeFiles.moveAsideAsBroken(file);
		assertEquals(dir.resolve("b.json.broken"), backup);
		assertEquals("garbage", Files.readString(backup));
		assertFalse(Files.exists(file));
	}

	@Test
	void moveAsideFailsForAMissingFile() {
		assertThrows(IOException.class, () -> SafeFiles.moveAsideAsBroken(dir.resolve("none.json")));
	}
}
