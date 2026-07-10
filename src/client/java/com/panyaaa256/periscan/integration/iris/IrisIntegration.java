package com.panyaaa256.periscan.integration.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the Iris integration. This class never references Iris types,
 * so it is safe to load without Iris installed; all direct access lives in
 * {@link IrisPipelineAssigner}, which is only class-loaded after
 * {@link #isAvailable()} passed.
 *
 * <p>Iris translates each {@link RenderPipeline} to a shader-pack program via
 * a lookup keyed on the pipeline instance. Custom pipelines are not in that
 * map, so with a shader pack active Iris has no program to draw them with and
 * the geometry is silently dropped. Registering the pipelines through Iris's
 * API restores rendering.
 */
public final class IrisIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger("periscan");
	private static Boolean available;

	private IrisIntegration() {
	}

	/**
	 * Registers a position+color pipeline with Iris so shader packs draw it
	 * with the basic (untextured) program. No-op when Iris is absent.
	 */
	public static void assignBasicPipeline(RenderPipeline pipeline) {
		if (isAvailable()) {
			IrisPipelineAssigner.assignBasic(pipeline);
		}
	}

	private static boolean isAvailable() {
		if (available == null) {
			available = probe();
		}
		return available;
	}

	private static boolean probe() {
		if (!FabricLoader.getInstance().isModLoaded("iris")) {
			return false;
		}
		// Capability probe instead of a version check: assignPipeline was added
		// to API v0 in a later revision, so verify the exact method used by
		// IrisPipelineAssigner.
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			api.getMethod("assignPipeline", RenderPipeline.class, program);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("PeriScan: iris is present but incompatible, highlights will not render "
					+ "while a shader pack is active: {}", e.toString());
			return false;
		}
	}
}
