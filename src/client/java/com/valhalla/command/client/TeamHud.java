package com.valhalla.command.client;

import com.valhalla.command.ValhallaCommand;
import com.valhalla.command.entity.VikingEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.List;

/** Small warband panel in the top-left: each warrior's name, level and health. */
public final class TeamHud {
	private static final int MAX_ROWS = 8;
	private static final int WIDTH = 104;
	private static final int ROW_H = 16;

	private TeamHud() {}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "team"), TeamHud::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		String me = mc.player.getUUID().toString();
		List<VikingEntity> team = mc.level.getEntitiesOfClass(VikingEntity.class,
				mc.player.getBoundingBox().inflate(96), v -> v.isAlive() && v.isHired() && me.equals(v.getOwnerString()));
		if (team.isEmpty()) return;
		// Highest level at the top; stable order so rows don't jump around
		team.sort(Comparator.comparingInt(VikingEntity::getVikingLevel).reversed()
				.thenComparing(v -> v.getCustomName() != null ? v.getCustomName().getString() : "")
				.thenComparingInt(VikingEntity::getId));

		int x = 4, y = 4;
		int shown = Math.min(MAX_ROWS, team.size());
		for (int i = 0; i < shown; i++) {
			VikingEntity v = team.get(i);
			float pct = Math.max(0f, Math.min(1f, v.getHealth() / Math.max(1f, v.getMaxHealth())));
			int barColor = pct > 0.5f ? 0xFF5FBF4A : pct > 0.25f ? 0xFFE0B23A : 0xFFD0453A;

			// Panel like a vanilla tooltip: dark fill, subtle edge
			g.fill(x, y, x + WIDTH, y + ROW_H - 2, 0xB0100C0A);
			g.fill(x, y, x + WIDTH, y + 1, 0x40FFFFFF);
			g.fill(x, y, x + 2, y + ROW_H - 2, barColor);

			String name = v.getCustomName() != null ? v.getCustomName().getString() : "Viking";
			String lvl = "Lv " + v.getVikingLevel();
			g.text(mc.font, name, x + 5, y + 2, 0xFFF2E6D0, true);
			g.text(mc.font, lvl, x + WIDTH - 3 - mc.font.width(lvl), y + 2, 0xFFB8A98F, true);

			// Health bar
			int bx = x + 5, by = y + ROW_H - 5, bw = WIDTH - 9;
			g.fill(bx, by, bx + bw, by + 2, 0xFF2A2420);
			g.fill(bx, by, bx + Math.round(bw * pct), by + 2, barColor);
			y += ROW_H;
		}
		if (team.size() > shown) {
			g.text(mc.font, "+" + (team.size() - shown) + " more", x + 5, y + 1, 0xFFB8A98F, true);
		}
	}
}
