package com.panyaaa256.periscan.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.panyaaa256.periscan.config.PeriScanConfig;
import com.panyaaa256.periscan.scan.ScanManager;
import com.panyaaa256.periscan.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
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

	/** Emits the cached faces or lines of one chunk, given the camera position. */
	@FunctionalInterface
	private interface MeshDrawer {
		void draw(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, int color, HighlightGeometry.Mesh mesh);
	}

	// Cached shapes per highlight group, indexed by Zone ordinal; colour is applied at draw time.
	private static final HighlightMeshes[] ZONE_MESHES = new HighlightMeshes[Zone.VALUES.length];
	private static final HighlightMeshes FALLING_X_MESHES = new HighlightMeshes();
	private static final HighlightMeshes FALLING_Z_MESHES = new HighlightMeshes();

	static {
		for (int i = 0; i < ZONE_MESHES.length; i++) {
			ZONE_MESHES[i] = new HighlightMeshes();
		}
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
			releaseMeshes();
			return;
		}
		// Highlights only exist in the dimension the region was started in.
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level.dimension() != scan.dimension()) {
			releaseMeshes();
			return;
		}
		updateMeshes();
		// Hidden by the user: the meshes are still kept up to date above, so showing
		// the highlights again needs no rebuild.
		if (!PeriScanConfig.get().showHighlights) {
			return;
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		PoseStack.Pose pose = context.matrixStack().last();

		// All fills first, then all lines: BufferSource batches by RenderType and
		// ends the previous batch when a different type is requested.
		addAllBoxes(context.consumers().getBuffer(FILL_TYPE), pose, camera, HighlightRenderer::boxFaces,
				HighlightRenderer::meshFaces, FILL_ALPHA, PENDING_FILL_ALPHA);
		addAllBoxes(context.consumers().getBuffer(LINE_TYPE), pose, camera, HighlightRenderer::boxEdges,
				HighlightRenderer::meshLines, 0xFF, 0xFF);
	}

	/** Rebuilds the meshes of the chunks that changed; called once per frame before anything is drawn. */
	private static void updateMeshes() {
		ScanManager scan = ScanManager.INSTANCE;
		for (Zone zone : Zone.VALUES) {
			ZONE_MESHES[zone.ordinal()].update(scan.highlightIndex(zone));
		}
		FALLING_X_MESHES.update(scan.fallingAlongXIndex());
		FALLING_Z_MESHES.update(scan.fallingAlongZIndex());
	}

	/** Drops the cached meshes while nothing is drawn, so they do not outlive the scan. */
	private static void releaseMeshes() {
		for (HighlightMeshes meshes : ZONE_MESHES) {
			meshes.release();
		}
		FALLING_X_MESHES.release();
		FALLING_Z_MESHES.release();
	}

	private static void addAllBoxes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, BoxDrawer drawer,
			MeshDrawer meshDrawer, int alpha, int pendingAlpha) {
		PeriScanConfig config = PeriScanConfig.get();
		for (Zone zone : Zone.VALUES) {
			if (zone.settings(config).visible) {
				addMeshes(buffer, pose, camera, meshDrawer, ZONE_MESHES[zone.ordinal()],
						argb(alpha, zone.settings(config).color));
			}
		}
		// Falling-block runs belong to the trench inner zone: they use its color and are hidden with it.
		if (config.trenchInner.visible) {
			int fallingColor = argb(alpha, config.trenchInner.color);
			addMeshes(buffer, pose, camera, meshDrawer, FALLING_X_MESHES, fallingColor);
			addMeshes(buffer, pose, camera, meshDrawer, FALLING_Z_MESHES, fallingColor);
		}
		if (config.showPendingChunks) {
			addPendingChunkBoxes(buffer, pose, camera, drawer, argb(pendingAlpha, config.pendingChunkColor));
		}
	}

	private static int argb(int alpha, Color color) {
		return (alpha << 24) | (color.getRGB() & 0xFFFFFF);
	}

	/** Streams the cached meshes into the buffer, converting to camera-relative coordinates. */
	private static void addMeshes(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, MeshDrawer drawer,
			HighlightMeshes meshes, int color) {
		for (HighlightGeometry.Mesh mesh : meshes.meshes()) {
			drawer.draw(buffer, pose, camera, color, mesh);
		}
	}

	private static void meshFaces(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, int color,
			HighlightGeometry.Mesh mesh) {
		int[] faces = mesh.faces();
		for (int i = 0; i < faces.length; i += HighlightGeometry.FACE_STRIDE) {
			float x0 = (float) (faces[i] - camera.x);
			float y0 = (float) (faces[i + 1] - camera.y);
			float z0 = (float) (faces[i + 2] - camera.z);
			float x1 = x0 + 1;
			float y1 = y0 + 1;
			float z1 = z0 + 1;
			switch (faces[i + 3]) {
				case HighlightGeometry.DOWN -> quad(buffer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
				case HighlightGeometry.UP -> quad(buffer, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				case HighlightGeometry.NORTH -> quad(buffer, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
				case HighlightGeometry.SOUTH -> quad(buffer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				case HighlightGeometry.WEST -> quad(buffer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				default -> quad(buffer, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
			}
		}
	}

	private static void meshLines(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, int color,
			HighlightGeometry.Mesh mesh) {
		int[] lines = mesh.lines();
		for (int i = 0; i < lines.length; i += HighlightGeometry.LINE_STRIDE) {
			line(buffer, pose, color,
					(float) (lines[i] - camera.x), (float) (lines[i + 1] - camera.y), (float) (lines[i + 2] - camera.z),
					(float) (lines[i + 3] - camera.x), (float) (lines[i + 4] - camera.y), (float) (lines[i + 5] - camera.z));
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
