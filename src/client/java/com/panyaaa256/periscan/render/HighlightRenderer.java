package com.panyaaa256.periscan.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

/**
 * Draws highlighted blocks as translucent filled boxes with outlines, visible
 * through terrain (depth test disabled).
 */
public final class HighlightRenderer {
	private static final int FILL_ALPHA = 0x40;
	private static final int PENDING_FILL_ALPHA = 0x18;
	// Pending chunk boxes are only drawn within this horizontal distance of the camera.
	private static final double PENDING_RENDER_DISTANCE = 2048.0;

	private static final RenderType FILL_TYPE = renderType("periscan_highlight_fill", VertexFormat.Mode.QUADS);
	private static final RenderType LINE_TYPE = renderType("periscan_highlight_lines", VertexFormat.Mode.DEBUG_LINES);

	/** Emits one axis-aligned box (camera-relative coordinates) as faces or edges. */
	@FunctionalInterface
	private interface BoxDrawer {
		void draw(VertexConsumer buffer, PoseStack.Pose pose, int color,
				float x0, float y0, float z0, float x1, float y1, float z1);
	}

	private HighlightRenderer() {
	}

	/**
	 * Translucent position+color render type that draws through terrain. The
	 * position_color shader applies no fog, so highlights beyond the render
	 * distance keep their color. Iris maps vanilla shaders to shader-pack
	 * programs by itself before 1.21.5, so no Iris integration is needed here.
	 */
	private static RenderType renderType(String name, VertexFormat.Mode mode) {
		// The BufferSource the geometry goes through has a small default buffer,
		// too small for large scans.
		return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, mode, 1 << 20,
				RenderType.CompositeState.builder()
						.setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
						.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
						.setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
						.setWriteMaskState(RenderStateShard.COLOR_WRITE)
						.setCullState(RenderStateShard.NO_CULL)
						.createCompositeState(false));
	}

	public static void init() {
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
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		PoseStack.Pose pose = context.matrixStack().last();

		// All fills first, then all lines: BufferSource batches by RenderType and
		// ends the previous batch when a different type is requested.
		addAllBoxes(context.consumers().getBuffer(FILL_TYPE), pose, camera, HighlightRenderer::boxFaces,
				FILL_ALPHA, PENDING_FILL_ALPHA);
		addAllBoxes(context.consumers().getBuffer(LINE_TYPE), pose, camera, HighlightRenderer::boxEdges, 0xFF, 0xFF);
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
		buffer.vertex(pose.pose(), ax, ay, az).color(color).endVertex();
		buffer.vertex(pose.pose(), bx, by, bz).color(color).endVertex();
		buffer.vertex(pose.pose(), cx, cy, cz).color(color).endVertex();
		buffer.vertex(pose.pose(), dx, dy, dz).color(color).endVertex();
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
		buffer.vertex(pose.pose(), ax, ay, az).color(color).endVertex();
		buffer.vertex(pose.pose(), bx, by, bz).color(color).endVertex();
	}
}
