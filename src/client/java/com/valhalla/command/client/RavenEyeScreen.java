package com.valhalla.command.client;

import com.valhalla.command.entity.VikingEntity;
import com.valhalla.command.network.RavenOrderPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Raven's Eye: every creature in range gets a bracket. The mouse snaps to the nearest one.
 * Left click = mark/unmark. Click empty ground or water = mark an area. Right click = clear.
 * Enter, Esc or the wheel key = send the ravens.
 */
public class RavenEyeScreen extends Screen {
	private static final double RANGE = 80;
	private static final float SNAP_PX = 48;
	private static final int COLOR_HOSTILE = 0xFFE8D8C0;
	private static final int COLOR_OTHER = 0x88FFFFFF;
	private static final int COLOR_HOVER = 0xFFFFE08A;
	private static final int COLOR_MARKED = 0xFFE03C3C;

	private final Set<Integer> marked = new HashSet<>(RavenMarks.marked);
	private @Nullable Vec3 area = RavenMarks.area;
	private final List<LivingEntity> candidates = new ArrayList<>();
	private @Nullable LivingEntity hovered;
	private boolean wasLeft = true, wasRight = true, wasEnter = true, wasWheel = true;
	private boolean sent;

	public RavenEyeScreen() {
		super(Component.literal("Raven's Eye"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// A cold, dark vignette so it feels like seeing through the raven's eyes
		g.fillGradient(0, 0, this.width, this.height / 4, 0x70000814, 0x00000814);
		g.fillGradient(0, this.height * 3 / 4, this.width, this.height, 0x00000814, 0x70000814);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (this.minecraft.player == null || this.minecraft.level == null) return;
		Projector.setup(delta, this.width, this.height);
		String me = this.minecraft.player.getUUID().toString();

		candidates.clear();
		candidates.addAll(this.minecraft.level.getEntitiesOfClass(LivingEntity.class,
				this.minecraft.player.getBoundingBox().inflate(RANGE),
				e -> e != this.minecraft.player && e.isAlive() && e.getType() != EntityTypes.ARMOR_STAND
						&& !(e instanceof VikingEntity v && me.equals(v.getOwnerString()))));

		// Snap to the closest bracket on screen
		hovered = null;
		float best = SNAP_PX * SNAP_PX;
		for (LivingEntity e : candidates) {
			float[] c = Projector.project(e.position().add(0, e.getBbHeight() / 2, 0));
			if (c == null) continue;
			float dx = c[0] - mouseX, dy = c[1] - mouseY, d = dx * dx + dy * dy;
			if (d < best) { best = d; hovered = e; }
		}

		for (LivingEntity e : candidates) drawBracket(g, e);
		if (area != null) RavenMarks.drawRaven(g, area.add(0, 1.5, 0), 16);

		// Hovered info
		if (hovered != null) {
			float[] top = Projector.project(hovered.position().add(0, hovered.getBbHeight() + 0.3, 0));
			if (top != null) {
				String info = hovered.getName().getString() + "  " + Math.round(hovered.getHealth()) + "/" + Math.round(hovered.getMaxHealth()) + " ❤";
				int tw = this.font.width(info);
				g.text(this.font, info, Math.round(top[0]) - tw / 2, Math.round(top[1]) - 14, 0xFFFFFFFF, true);
			}
		}

		// Help bar
		String help = "Click: mark  •  Click ground: mark area  •  Right click: clear  •  Enter: send ravens";
		g.text(this.font, help, this.width / 2 - this.font.width(help) / 2, this.height - 22, 0xFFD8CCB4, true);
		String count = marked.size() + " marked" + (area != null ? " + area" : "");
		g.text(this.font, count, this.width / 2 - this.font.width(count) / 2, 12, 0xFFE03C3C, true);

		handleInput(mouseX, mouseY);
	}

	private void drawBracket(GuiGraphicsExtractor g, LivingEntity e) {
		float[] top = Projector.project(e.position().add(0, e.getBbHeight() + 0.1, 0));
		float[] bot = Projector.project(e.position());
		if (top == null || bot == null) return;
		int h = Math.max(8, Math.round(bot[1] - top[1]));
		int w = Math.max(8, Math.round(h * 0.6f));
		int x = Math.round(top[0]) - w / 2, y = Math.round(top[1]);
		boolean isMarked = marked.contains(e.getId());
		int color = isMarked ? COLOR_MARKED : e == hovered ? COLOR_HOVER
				: e.getType().getCategory() == MobCategory.MONSTER || e instanceof VikingEntity ? COLOR_HOSTILE : COLOR_OTHER;
		int t = e == hovered ? 2 : 1;
		int len = Math.max(3, Math.min(w, h) / 4);
		// four corners
		g.fill(x, y, x + len, y + t, color);           g.fill(x, y, x + t, y + len, color);
		g.fill(x + w - len, y, x + w, y + t, color);   g.fill(x + w - t, y, x + w, y + len, color);
		g.fill(x, y + h - t, x + len, y + h, color);   g.fill(x, y + h - len, x + t, y + h, color);
		g.fill(x + w - len, y + h - t, x + w, y + h, color); g.fill(x + w - t, y + h - len, x + w, y + h, color);
		if (isMarked) RavenMarks.drawRaven(g, e.position().add(0, e.getBbHeight() + 0.7, 0), 12);
	}

	private void handleInput(int mouseX, int mouseY) {
		long win = this.minecraft.getWindow().handle();
		boolean left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		boolean enter = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS;
		int wheelCode = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
				.getBoundKeyOf(ValhallaCommandClient.WHEEL_KEY).getValue();
		boolean wheel = wheelCode >= 0 && GLFW.glfwGetKey(win, wheelCode) == GLFW.GLFW_PRESS;

		if (left && !wasLeft) {
			if (hovered != null) {
				if (!marked.remove(hovered.getId())) marked.add(hovered.getId());
			} else {
				// Mark the ground / water under the mouse
				Vec3 from = Projector.camera();
				Vec3 to = from.add(Projector.direction(mouseX, mouseY).scale(120));
				HitResult hit = this.minecraft.level.clip(new ClipContext(from, to,
						ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this.minecraft.player));
				if (hit.getType() != HitResult.Type.MISS) area = hit.getLocation();
			}
		}
		if (right && !wasRight) {
			marked.clear();
			area = null;
		}
		if ((enter && !wasEnter) || (wheel && !wasWheel)) {
			this.onClose();
		}
		wasLeft = left; wasRight = right; wasEnter = enter; wasWheel = wheel;
	}

	@Override
	public void onClose() {
		if (!sent) {
			sent = true;
			RavenMarks.set(marked, area);
			if (!marked.isEmpty() || area != null) {
				Vec3 a = area != null ? area : Vec3.ZERO;
				ClientPlayNetworking.send(new RavenOrderPayload(new ArrayList<>(marked), area != null, a.x, a.y, a.z));
			}
		}
		super.onClose();
	}
}
