package com.panyaaa256.periscan;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What both sides share: the mod id, the logger and identifiers. */
public class PeriScan {
	public static final String MOD_ID = "periscan";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** An identifier in this mod's namespace. */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
