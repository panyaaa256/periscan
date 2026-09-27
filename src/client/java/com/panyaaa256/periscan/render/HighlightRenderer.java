package com.panyaaa256.periscan.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.panyaaa256.periscan.PeriScanClient;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.integration.iris.IrisIntegration;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Draws highlighted blocks as translucent filled boxes with outlines, visible
 * through terrain (depth test disabled).
 */
public final class HighlightRenderer {
	private static final int FILL_ALPHA = 0x40;
	private static final int PENDING_FILL_ALPHA = 0x18;
	// Pending chunk boxes are only drawn within this horizontal distance of the camera.
	private static final double PENDING_RENDER_DISTANCE = 2048.0;

	private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(PeriScanClient.id("pipeline/highlight_fill"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.build());

	private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(PeriScanClient.id("pipeline/highlight_lines"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES)
			.build());

	private static final RenderType FILL_TYPE = RenderType.create("periscan_highlight_fill",
			RenderSetup.builder(FILL_PIPELINE).bufferSize(1 << 20).createRenderSetup());
	private static final RenderType LINE_TYPE = RenderType.create("periscan_highlight_lines",
			RenderSetup.builder(LINE_PIPELINE).bufferSize(1 << 20).createRenderSetup());

	private HighlightRenderer() {
	}

	public static void init() {
		// Iris only draws pipelines it can map to a shader-pack program; without
		// this the highlights are invisible whenever a shader pack is active.
		IrisIntegration.assignBasicPipeline(FILL_PIPELINE);
		IrisIntegration.assignBasicPipeline(LINE_PIPELINE);
		WorldRenderEvents.AFTER_ENTITIES.register(HighlightRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		ScanManager scan = ScanManager.INSTANCE;
		if (!scan.isActive()) {
			return;
		}
		// Highlights only exist in the dimension the region was started in.
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level.dimension() != scan.dimension()) {
			return;
		}
		PeriScanConfig config = PeriScanConfig.get();
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		PoseStack.Pose pose = context.matrices().last();
		MultiBufferSource consumers = context.consumers();

		// All fills first, then all lines: BufferSource batches by RenderType and
		// ends the previous batch when a different type is requested.
		// Falling-block runs belong to the trench inner zone and use its color.
		int fallingRgb = config.trenchInner.color.getRGB() & 0xFFFFFF;

		VertexConsumer fill = consumers.getBuffer(FILL_TYPE);
		for (Zone zone : Zone.VALUES) {
			int color = (FILL_ALPHA << 24) | (zone.settings(config).color.getRGB() & 0xFFFFFF);
			LongIterator it = scan.highlights(zone).iterator();
			while (it.hasNext()) {
				addBoxFaces(fill, pose, camera, it.nextLong(), color);
			}
		}
		for (LongOpenHashSet set : new LongOpenHashSet[] { scan.fallingAlongX(), scan.fallingAlongZ() }) {
			LongIterator it = set.iterator();
			while (it.hasNext()) {
				addBoxFaces(fill, pose, camera, it.nextLong(), (FILL_ALPHA << 24) | fallingRgb);
			}
		}
		if (config.showPendingChunks) {
			addPendingChunkBoxes(fill, pose, camera, false,
					(PENDING_FILL_ALPHA << 24) | (config.pendingChunkColor.getRGB() & 0xFFFFFF));
		}

		VertexConsumer lines = consumers.getBuffer(LINE_TYPE);
		for (Zone zone : Zone.VALUES) {
			int color = 0xFF000000 | (zone.settings(config).color.getRGB() & 0xFFFFFF);
			LongIterator it = scan.highlights(zone).iterator();
			while (it.hasNext()) {
				addBoxEdges(lines, pose, camera, it.nextLong(), color);
			}
		}
		for (LongOpenHashSet set : new LongOpenHashSet[] { scan.fallingAlongX(), scan.fallingAlongZ() }) {
			LongIterator it = set.iterator();
			while (it.hasNext()) {
				addBoxEdges(lines, pose, camera, it.nextLong(), 0xFF000000 | fallingRgb);
			}
		}
		if (config.showPendingChunks) {
			addPendingChunkBoxes(lines, pose, camera, true,
					0xFF000000 | (config.pendingChunkColor.getRGB() & 0xFFFFFF));
		}
	}

	/** Draws one box over each unscanned chunk near the camera, spanning the scan's Y range. */
	private static void addPendingChunkBoxes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera,
			boolean edges, int color) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		PeriScanConfig config = PeriScanConfig.get();
		float y0 = (float) (Math.max(level.getMinY(), ScanManager.scanMinY(level)) - camera.y);
		float y1 = (float) (Math.min(level.getMaxY(), config.scanMaxY) + 1 - camera.y);
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
			if (edges) {
				boxEdges(buffer, pose, color, x0, y0, z0, x0 + 16, y1, z0 + 16);
			} else {
				boxFaces(buffer, pose, color, x0, y0, z0, x0 + 16, y1, z0 + 16);
			}
		}
	}

	private static void addBoxFaces(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, long posKey, int color) {
		float x0 = (float) (BlockPos.getX(posKey) - camera.x);
		float y0 = (float) (BlockPos.getY(posKey) - camera.y);
		float z0 = (float) (BlockPos.getZ(posKey) - camera.z);
		boxFaces(buffer, pose, color, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1);
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

	private static void addBoxEdges(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, long posKey, int color) {
		float x0 = (float) (BlockPos.getX(posKey) - camera.x);
		float y0 = (float) (BlockPos.getY(posKey) - camera.y);
		float z0 = (float) (BlockPos.getZ(posKey) - camera.z);
		boxEdges(buffer, pose, color, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1);
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
