plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.8"

// Sources are written against the newest version (26.3). These string
// replacements rename things back for older versions, so renames need no
// `//? if` blocks in the code. Each replace(old, new) turns `old` into `new`
// when the condition holds, and `new` back into `old` otherwise.
// https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/replacements
stonecutter parameters {
	replacements {
		// 1.21.6 reworked uniforms; the matrices+fog snippet replaced the
		// matrices+color one that vanilla's position_color pipelines use in 1.21.5.
		string(current.parsed >= "1.21.6") {
			replace("RenderPipelines.MATRICES_COLOR_SNIPPET", "RenderPipelines.MATRICES_FOG_SNIPPET")
		}
		// 1.21.11: ResourceLocation was renamed to Identifier, and RenderType
		// moved to its own package.
		string(current.parsed >= "1.21.11") {
			replace("ResourceLocation", "Identifier")
			replace(".location()", ".identifier()")
			replace("net.minecraft.client.renderer.RenderType;", "net.minecraft.client.renderer.rendertype.RenderType;")
		}
		// 26.1 (unobfuscated): Fabric API took Mojang's names, and access
		// wideners use the official namespace.
		string(current.parsed >= "26.1") {
			replace("ClientCommandManager", "ClientCommands")
			replace("source.getWorld()", "source.getLevel()")
			replace("rendering.v1.world.", "rendering.v1.level.")
			replace("WorldRenderEvents", "LevelRenderEvents")
			replace("WorldRenderContext", "LevelRenderContext")
			replace("accessWidener v2 named", "classTweaker v1 official")
		}
		// 26.2: the current screen moved from Minecraft to Gui.
		string(current.parsed >= "26.2") {
			replace("client.screen", "client.gui.screen()")
			replace("client.setScreen(", "client.gui.setScreen(")
		}
		// 26.3: Blaze3d's render API moved to Renderpearl; PushReaction constants
		// and RedStoneWireBlock were renamed.
		string(current.parsed >= "26.3") {
			replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
			replace("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
			replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
			replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
			replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
			replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
			replace("PushReaction.DESTROY", "PushReaction.POPPED")
			replace("case BLOCK ->", "case IMMOVEABLE ->")
			replace("case DESTROY, PUSH_ONLY ->", "case POPPED, PUSH ->")
			replace("RedStoneWireBlock", "RedstoneWireBlock")
		}
	}
}
