package com.panyaaa256.periscan.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.integration.iris.IrisIntegration;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Draws highlighted blocks as translucent filled boxes with outlines, visible
 * through terrain (depth test always passes).
 */
public final class HighlightRenderer {
	private static final int FILL_ALPHA = 0x40;
	private static final int PENDING_FILL_ALPHA = 0x18;
	// Pending chunk boxes are only drawn within this horizontal distance of the camera.
	private static final double PENDING_RENDER_DISTANCE = 2048.0;

	private static final RenderPipeline FILL_PIPELINE = pipeline("pipeline/highlight_fill", false);
	private static final RenderPipeline LINE_PIPELINE = pipeline("pipeline/highlight_lines", true);

	private static final RenderType FILL_TYPE = renderType("periscan_highlight_fill", FILL_PIPELINE);
	private static final RenderType LINE_TYPE = renderType("periscan_highlight_lines", LINE_PIPELINE);

	/** Emits one axis-aligned box (camera-relative coordinates) as faces or edges. */
	@FunctionalInterface
	private interface BoxDrawer {
		void draw(VertexConsumer buffer, PoseStack.Pose pose, int color,
				float x0, float y0, float z0, float x1, float y1, float z1);
	}

	private HighlightRenderer() {
	}

	/**
	 * Translucent position+color pipeline that draws through terrain. The
	 * position_color shader applies no fog, so highlights beyond the render
	 * distance keep their color (vanilla's line shader would fade them to fog).
	 */
	private static RenderPipeline pipeline(String path, boolean lines) {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(PeriScanClient.id(path))
				.withVertexShader("core/position_color")
				.withFragmentShader("core/position_color")
				.withCull(false);
		// 26.1 grouped blending and depth settings into target/stencil states.
		//? if >=26.1 {
		builder.withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(new com.mojang.renderpearl.api.pipeline.DepthStencilState(
						com.mojang.renderpearl.api.pipeline.CompareOp.ALWAYS_PASS, false));
		//?} else {
		/*builder.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false);
		*///?}
		// 26.2 split the vertex format into a vertex binding and a primitive topology.
		//? if >=26.2 {
		builder.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
				.withPrimitiveTopology(lines
						? com.mojang.renderpearl.api.pipeline.PrimitiveTopology.DEBUG_LINES
						: com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS);
		//?} else {
		/*builder.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, lines
				? com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES
				: com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS);
		*///?}
		return RenderPipelines.register(builder.build());
	}

	private static RenderType renderType(String name, RenderPipeline pipeline) {
		// 1.21.11 replaced RenderType's composite state with RenderSetup. On
		// 1.21.x the geometry goes through a shared BufferSource, whose default
		// buffer is too small for large scans.
		//? if >=26.1 {
		return RenderType.create(name, net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline)
				.createRenderSetup());
		//?} elif >=1.21.11 {
		/*return RenderType.create(name, net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline)
				.bufferSize(1 << 20).createRenderSetup());
		*///?} else {
		/*return RenderType.create(name, 1 << 20, pipeline, RenderType.CompositeState.builder().createCompositeState(false));
		*///?}
	}

	public static void init() {
		// Iris only draws pipelines it can map to a shader-pack program; without
		// this the highlights are invisible whenever a shader pack is active.
		IrisIntegration.assignBasicPipeline(FILL_PIPELINE);
		IrisIntegration.assignBasicPipeline(LINE_PIPELINE);
		//? if >=26.1 {
		LevelRenderEvents.COLLECT_SUBMITS.register(HighlightRenderer::render);
		//?} else
		//LevelRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		ScanManager scan = ScanManager.INSTANCE;
		if (!scan.isActive()) {
			return;
		}
		// Highlights only exist in the dimension the region was started in.
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level.dimension() != scan.dimension()) {
			return;
		}
		//? if >=26.1 {
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();

		// One submit per render type: all fills in one batch, all lines in another.
		context.submitNodeCollector().submitCustomGeometry(poseStack, FILL_TYPE, (pose, fill) ->
				addAllBoxes(fill, pose, camera, HighlightRenderer::boxFaces, FILL_ALPHA, PENDING_FILL_ALPHA));
		context.submitNodeCollector().submitCustomGeometry(poseStack, LINE_TYPE, (pose, lines) ->
				addAllBoxes(lines, pose, camera, HighlightRenderer::boxEdges, 0xFF, 0xFF));
		//?} else {
		/*Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		PoseStack.Pose pose = context.matrices().last();

		// All fills first, then all lines: BufferSource batches by RenderType and
		// ends the previous batch when a different type is requested.
		addAllBoxes(context.consumers().getBuffer(FILL_TYPE), pose, camera, HighlightRenderer::boxFaces,
				FILL_ALPHA, PENDING_FILL_ALPHA);
		addAllBoxes(context.consumers().getBuffer(LINE_TYPE), pose, camera, HighlightRenderer::boxEdges, 0xFF, 0xFF);
		*///?}
	}

	private static void addAllBoxes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, BoxDrawer drawer,
			int alpha, int pendingAlpha) {
		ScanManager scan = ScanManager.INSTANCE;
		PeriScanConfig config = PeriScanConfig.get();
		for (Zone zone : Zone.VALUES) {
			addBlockBoxes(buffer, pose, camera, drawer, scan.highlights(zone), argb(alpha, zone.settings(config).color));
		}
		// Falling-block runs belong to the trench inner zone and use its color.
		int fallingColor = argb(alpha, config.trenchInner.color);
		addBlockBoxes(buffer, pose, camera, drawer, scan.fallingAlongX(), fallingColor);
		addBlockBoxes(buffer, pose, camera, drawer, scan.fallingAlongZ(), fallingColor);
		if (config.showPendingChunks) {
			addPendingChunkBoxes(buffer, pose, camera, drawer, argb(pendingAlpha, config.pendingChunkColor));
		}
	}

	private static int argb(int alpha, Color color) {
		return (alpha << 24) | (color.getRGB() & 0xFFFFFF);
	}

	/** Draws a one-block box at every position (BlockPos longs) in the set. */
	private static void addBlockBoxes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, BoxDrawer drawer,
			LongOpenHashSet positions, int color) {
		LongIterator it = positions.iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			float x0 = (float) (BlockPos.getX(key) - camera.x);
			float y0 = (float) (BlockPos.getY(key) - camera.y);
			float z0 = (float) (BlockPos.getZ(key) - camera.z);
			drawer.draw(buffer, pose, color, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1);
		}
	}

	/** Draws one box over each unscanned chunk near the camera, spanning the scan's Y range. */
	private static void addPendingChunkBoxes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera,
			BoxDrawer drawer, int color) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float y0 = (float) (ScanManager.scanMinY(level) - camera.y);
		float y1 = (float) (ScanManager.scanMaxY(level) + 1 - camera.y);
		if (y1 <= y0) {
			return;
		}
		LongIterator it = ScanManager.INSTANCE.pendingChunks().iterator();
		while (it.hasNext()) {
			long key = it.nextLong();
			double blockX = ChunkPos.getX(key) * 16.0;
			double blockZ = ChunkPos.getZ(key) * 16.0;
			double dx = blockX + 8 - camera.x;
			double dz = blockZ + 8 - camera.z;
			if (dx * dx + dz * dz > PENDING_RENDER_DISTANCE * PENDING_RENDER_DISTANCE) {
				continue;
			}
			float x0 = (float) (blockX - camera.x);
			float z0 = (float) (blockZ - camera.z);
			drawer.draw(buffer, pose, color, x0, y0, z0, x0 + 16, y1, z0 + 16);
		}
	}

	private static void boxFaces(VertexConsumer buffer, PoseStack.Pose pose, int color,
			float x0, float y0, float z0, float x1, float y1, float z1) {
		quad(buffer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
		quad(buffer, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
		quad(buffer, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north (z0)
		quad(buffer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south (z1)
		quad(buffer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west (x0)
		quad(buffer, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east (x1)
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int color,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		buffer.addVertex(pose, ax, ay, az).setColor(color);
		buffer.addVertex(pose, bx, by, bz).setColor(color);
		buffer.addVertex(pose, cx, cy, cz).setColor(color);
		buffer.addVertex(pose, dx, dy, dz).setColor(color);
	}

	private static void boxEdges(VertexConsumer buffer, PoseStack.Pose pose, int color,
			float x0, float y0, float z0, float x1, float y1, float z1) {
		// bottom rectangle
		line(buffer, pose, color, x0, y0, z0, x1, y0, z0);
		line(buffer, pose, color, x1, y0, z0, x1, y0, z1);
		line(buffer, pose, color, x1, y0, z1, x0, y0, z1);
		line(buffer, pose, color, x0, y0, z1, x0, y0, z0);
		// top rectangle
		line(buffer, pose, color, x0, y1, z0, x1, y1, z0);
		line(buffer, pose, color, x1, y1, z0, x1, y1, z1);
		line(buffer, pose, color, x1, y1, z1, x0, y1, z1);
		line(buffer, pose, color, x0, y1, z1, x0, y1, z0);
		// vertical edges
		line(buffer, pose, color, x0, y0, z0, x0, y1, z0);
		line(buffer, pose, color, x1, y0, z0, x1, y1, z0);
		line(buffer, pose, color, x1, y0, z1, x1, y1, z1);
		line(buffer, pose, color, x0, y0, z1, x0, y1, z1);
	}

	private static void line(VertexConsumer buffer, PoseStack.Pose pose, int color,
			float ax, float ay, float az, float bx, float by, float bz) {
		buffer.addVertex(pose, ax, ay, az).setColor(color);
		buffer.addVertex(pose, bx, by, bz).setColor(color);
	}
}
