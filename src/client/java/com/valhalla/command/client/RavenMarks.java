package com.valhalla.command.client;

import com.valhalla.command.ValhallaCommand;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** What you marked with Raven's Eye, and the raven icons floating over them (only you see these). */
public final class RavenMarks {
	public static final Identifier RAVEN = Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "textures/gui/raven.png");
	public static final Set<Integer> marked = new HashSet<>();
	public static @Nullable Vec3 area;
	private static long areaUntil;

	private RavenMarks() {}

	public static void set(Set<Integer> ids, @Nullable Vec3 spot) {
		marked.clear();
		marked.addAll(ids);
		area = spot;
		areaUntil = System.currentTimeMillis() + 60_000;
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "raven_marks"), RavenMarks::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (marked.isEmpty() && area == null) return;
		if (mc.gui.screen() instanceof RavenEyeScreen) return; // the screen draws its own
		Projector.setup(delta.getGameTimeDeltaPartialTick(true), g.guiWidth(), g.guiHeight());

		marked.removeIf(id -> { Entity e = mc.level.getEntity(id); return e == null || !e.isAlive(); });
		for (int id : marked) {
			Entity e = mc.level.getEntity(id);
			if (e != null) drawRaven(g, e.position().add(0, e.getBbHeight() + 0.7, 0), 12);
		}
		if (area != null) {
			if (System.currentTimeMillis() > areaUntil) area = null;
			else drawRaven(g, area.add(0, 1.5, 0), 14);
		}
	}

	public static void drawRaven(GuiGraphicsExtractor g, Vec3 world, int size) {
		float[] p = Projector.project(world);
		if (p == null) return;
		int x = Math.round(p[0]) - size / 2, y = Math.round(p[1]) - size / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, RAVEN, x, y, 0f, 0f, size, size, 32, 32, 32, 32);
	}
}
