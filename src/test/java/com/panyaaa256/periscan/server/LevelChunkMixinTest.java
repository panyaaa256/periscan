package com.panyaaa256.periscan.server;

import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tests run on Fabric Loader (fabric-loader-junit), which applies the
 * mod's mixins, so a mixin whose target is missing in this version fails here
 * instead of in game.
 */
class LevelChunkMixinTest {
	@Test
	void blockChangesAreHooked() {
		boolean hooked = Arrays.stream(LevelChunk.class.getDeclaredMethods())
				.map(Method::getName)
				.anyMatch(name -> name.contains("periscan$onSetBlockState"));
		assertTrue(hooked, "LevelChunkMixin was not applied to LevelChunk");
	}
}
