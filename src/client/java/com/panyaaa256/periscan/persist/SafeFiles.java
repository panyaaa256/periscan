package com.panyaaa256.periscan.persist;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The two file operations every PeriScan data file needs: replacing a file
 * without ever leaving it half written, and setting aside a file that cannot
 * be read. Both throw instead of logging, because what to say (and whether
 * saving may go on) differs per file.
 */
public final class SafeFiles {
	private SafeFiles() {
	}

	/**
	 * Writes the text to a temporary file that then replaces the file, so a
	 * crash mid-write cannot leave a truncated file (which the next read would
	 * move aside). Creates the parent folder if needed.
	 */
	public static void writeAtomically(Path file, String content) throws IOException {
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.createDirectories(file.getParent());
		Files.writeString(temp, content);
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Moves an unreadable file to {@code <name>.broken}, replacing an older
	 * one, so the next save does not overwrite what the user may want to repair.
	 *
	 * @return the path it was moved to
	 */
	public static Path moveAsideAsBroken(Path file) throws IOException {
		Path backup = file.resolveSibling(file.getFileName() + ".broken");
		Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
		return backup;
	}
}
