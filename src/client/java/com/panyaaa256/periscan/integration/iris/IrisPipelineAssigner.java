package com.panyaaa256.periscan.integration.iris;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

/**
 * The only class that references Iris types directly. Must not be class-loaded
 * before {@code IrisIntegration.isAvailable()} passed.
 */
final class IrisPipelineAssigner {
	private IrisPipelineAssigner() {
	}

	static void assignBasic(RenderPipeline pipeline) {
		IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.BASIC);
	}
}
