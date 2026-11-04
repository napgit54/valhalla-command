package com.valhalla.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.valhalla.command.ValhallaCommand;
import com.valhalla.command.entity.VikingEntity;
import com.valhalla.command.item.ModItems;
import com.valhalla.command.network.VikingActionPayload;
import com.valhalla.command.network.WheelCommandPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The radial wheel. Two modes:
 *  - COMMAND: hold the wheel key, give orders to your warband.
 *  - HIRE: hold right click on a Viking for hire; only "Hire" is available.
 * Each section is its own texture (cut from the wheel artwork) so it can animate on its own.
 */
public class CommandWheelScreen extends Screen {
	public enum Mode { COMMAND, HIRE, MANAGE }

	private static final int SEGMENTS = 6;

	// ---- Command mode: clockwise from the top ----
	private static final String[] COMMAND_LABELS = { "Follow", "Charge", "Attack", "Defend", "Hold", "Retreat" };
	private static final ItemStack[] COMMAND_ICONS = {
			new ItemStack(Items.LEAD),          // Follow
			new ItemStack(Items.IRON_AXE),      // Charge
			new ItemStack(Items.IRON_SWORD),    // Attack
			new ItemStack(Items.SHIELD),        // Defend
			new ItemStack(Items.ANVIL),         // Hold
			new ItemStack(Items.LEATHER_BOOTS)  // Retreat
	};
	/** These commands only work during a fight; greyed out otherwise. */
	private static final boolean[] NEEDS_FIGHT = { false, true, false, true, true, true };

	// ---- Manage mode (your own Viking): Aid on the right, Kick at the bottom ----
	private static final int MANAGE_AID = 1, MANAGE_KICK = 3;

	// ---- Textures (512x512, all aligned to the same canvas) ----
	private static final int TEX = 512;
	private static final Identifier[] SEG_TEX = new Identifier[SEGMENTS];
	private static final Identifier[] SEG_HOVER_TEX = new Identifier[SEGMENTS];
	private static final Identifier[] SEG_GREY_TEX = new Identifier[SEGMENTS];
	private static final Identifier[] SEG_RED_TEX = new Identifier[SEGMENTS];
	private static final Identifier CENTER_TEX = tex("center");
	static {
		for (int i = 0; i < SEGMENTS; i++) {
			SEG_TEX[i] = tex("seg" + i);
			SEG_HOVER_TEX[i] = tex("seg" + i + "_hover");
			SEG_GREY_TEX[i] = tex("seg" + i + "_grey");
			SEG_RED_TEX[i] = tex("seg" + i + "_hover_red");
		}
	}

	// ---- Look & feel (sizes in GUI units) ----
	private static final float WHEEL_SIZE = 190f;    // full diameter of the artwork
	private static final float INNER_RADIUS = 30f;   // inside this = centre (no selection)
	private static final float LABEL_RADIUS = 0.33f; // where icons sit, as a fraction of WHEEL_SIZE
	private static final float ICON_SCALE = 1.5f;
	private static final float HOVER_PUSH = 7f;
	private static final float HOVER_GROW = 0.04f;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_TEXT_DISABLED = 0xFF8A8A8A;

	// ---- Animation timings (ms) ----
	private static final float OPEN_MS = 220f;
	private static final float CLOSE_MS = 150f;
	private static final float STAGGER_MS = 28f;
	private static final float HOVER_SPEED = 14f;

	private final Mode mode;
	private final int targetId; // the Viking being hired (HIRE mode)
	private final String[] labels;
	private final @Nullable ItemStack[] icons;

	private final long openedAt = System.nanoTime();
	private long closingAt = -1;
	private long lastFrame = System.nanoTime();
	private final float[] hover = new float[SEGMENTS];
	private int hovered = -1;

	/** The normal command wheel. */
	public CommandWheelScreen() {
		this(Mode.COMMAND, -1);
	}

	public CommandWheelScreen(Mode mode, int targetId) {
		super(Component.literal(mode == Mode.COMMAND ? "Command Wheel" : "Viking"));
		this.mode = mode;
		this.targetId = targetId;
		if (mode == Mode.HIRE) {
			this.labels = new String[] { "Hire", "", "", "", "", "" };
			this.icons = new ItemStack[] { new ItemStack(ModItems.HACKSILVER), null, null, null, null, null };
		} else if (mode == Mode.MANAGE) {
			this.labels = new String[] { "", "Aid", "", "Kick", "", "" };
			this.icons = new ItemStack[] { null, new ItemStack(Items.GOLDEN_APPLE), null, new ItemStack(Items.BARRIER), null, null };
		} else {
			this.labels = COMMAND_LABELS;
			this.icons = COMMAND_ICONS;
		}
	}

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "textures/gui/wheel/" + name + ".png");
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// No dark/blurred background: the world stays visible behind the wheel.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
		lastFrame = now;

		// Real pixels per GUI unit, so the artwork is drawn at full resolution
		float scale = (float) this.minecraft.getWindow().getWidth() / Math.max(1, this.width);

		if (closingAt < 0 && !isHoldKeyDown()) {
			closingAt = now;
			onSelect(hovered);
		}

		float openMs = (now - openedAt) / 1_000_000f;
		float closeT = closingAt < 0 ? 0f : clamp01((now - closingAt) / 1_000_000f / CLOSE_MS);
		if (closingAt >= 0 && closeT >= 1f) {
			this.minecraft.gui.setScreen(null);
			return;
		}

		float cx = this.width / 2f;
		float cy = this.height / 2f;
		if (closingAt < 0) hovered = pickSegment(mouseX - cx, mouseY - cy);

		for (int i = 0; i < SEGMENTS; i++) {
			float target = (i == hovered && closingAt < 0) ? 1f : 0f;
			hover[i] += (target - hover[i]) * (1f - (float) Math.exp(-HOVER_SPEED * dt));
		}

		float closeEase = 1f - easeInCubic(closeT);
		int size = Math.round(WHEEL_SIZE * scale);
		int half = size / 2;

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / scale, 1f / scale);   // work in real pixels
		pose.translate(cx * scale, cy * scale);

		double step = Math.PI * 2 / SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			float p = clamp01((openMs - i * STAGGER_MS) / OPEN_MS);
			float s = easeOutBack(p) * closeEase;
			float alpha = clamp01(p * 1.6f) * closeEase;
			if (alpha <= 0.01f || s <= 0.01f) continue;

			float h = hover[i];
			double mid = -Math.PI / 2 + i * step;
			float push = (HOVER_PUSH * h + (1f - closeEase) * 10f) * scale;
			float grow = s * (1f + HOVER_GROW * h);
			boolean disabled = isDisabled(i);

			pose.pushMatrix();
			pose.translate((float) Math.cos(mid) * push, (float) Math.sin(mid) * push);
			pose.scale(grow, grow);

			// Normal section (or greyed out), with the brighter hover version faded in on top
			drawTex(graphics, disabled ? SEG_GREY_TEX[i] : SEG_TEX[i], half, size, withAlpha(0xFFFFFFFF, alpha));
			Identifier hoverTex = (mode == Mode.MANAGE && i == MANAGE_KICK) ? SEG_RED_TEX[i] : SEG_HOVER_TEX[i];
			if (h > 0.01f) drawTex(graphics, hoverTex, half, size, withAlpha(0xFFFFFFFF, alpha * h));

			// Icon with its name underneath, in the Minecraft font
			ItemStack icon = icons[i];
			if (alpha > 0.6f && icon != null) {
				float labelR = LABEL_RADIUS * size;
				pose.translate((float) Math.cos(mid) * labelR, (float) Math.sin(mid) * labelR);
				pose.scale(scale, scale); // back to GUI units

				pose.pushMatrix();
				pose.translate(0f, -5f);
				float iconScale = ICON_SCALE * (1f + 0.15f * h);
				pose.scale(iconScale, iconScale);
				graphics.fakeItem(icon, -8, -8);
				pose.popMatrix();

				String label = labels[i];
				int tw = this.font.width(label);
				int base = disabled ? COLOR_TEXT_DISABLED : COLOR_TEXT;
				int textColor = withAlpha(lerpColor(base, 0xFFFFF3C4, h), alpha);
				graphics.text(this.font, label, -tw / 2, 9, textColor, !disabled);
			}
			pose.popMatrix();
		}

		// Centre shield boss
		float cp = clamp01(openMs / (OPEN_MS * 0.8f));
		float cs = easeOutBack(cp) * closeEase;
		float ca = clamp01(cp * 2f) * closeEase;
		if (cs > 0.01f) {
			pose.pushMatrix();
			pose.scale(cs, cs);
			drawTex(graphics, CENTER_TEX, half, size, withAlpha(0xFFFFFFFF, ca));
			pose.popMatrix();
		}

		pose.popMatrix();
	}

	private static void drawTex(GuiGraphicsExtractor g, Identifier id, int half, int size, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, -half, -half, 0f, 0f, size, size, TEX, TEX, TEX, TEX, color);
	}

	/** Which section the mouse points at, or -1 for the centre or a greyed section. */
	private int pickSegment(float x, float y) {
		if (x * x + y * y < INNER_RADIUS * INNER_RADIUS) return -1;
		double step = Math.PI * 2 / SEGMENTS;
		double a = Math.atan2(y, x) + Math.PI / 2 + step / 2; // 0 = top, clockwise
		a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
		int seg = (int) (a / step) % SEGMENTS;
		return isDisabled(seg) ? -1 : seg;
	}

	private boolean isDisabled(int i) {
		if (mode == Mode.HIRE) {
			if (i != 0) return true;
			VikingEntity v = target();
			return v == null || v.isHired() || this.minecraft.player == null
					|| ModItems.countHacksilver(this.minecraft.player) < v.getPrice();
		}
		if (mode == Mode.MANAGE) {
			if (i == MANAGE_KICK) return target() == null;
			if (i == MANAGE_AID) return target() == null || this.minecraft.player == null
					|| !hasItem(Items.GOLDEN_APPLE);
			return true;
		}
		return NEEDS_FIGHT[i] && !AttackTracker.isAttacking();
	}

	private boolean hasItem(net.minecraft.world.item.Item item) {
		var inv = this.minecraft.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
		return false;
	}

	private @Nullable VikingEntity target() {
		if (this.minecraft.level == null) return null;
		return this.minecraft.level.getEntity(targetId) instanceof VikingEntity v ? v : null;
	}

	/** Called when the wheel closes. -1 means nothing was picked. */
	private void onSelect(int index) {
		if (index < 0) return;
		if (mode == Mode.HIRE) {
			ClientPlayNetworking.send(new VikingActionPayload(targetId, VikingEntity.ACTION_HIRE));
		} else if (mode == Mode.MANAGE) {
			int action = index == MANAGE_KICK ? VikingEntity.ACTION_KICK : VikingEntity.ACTION_AID;
			ClientPlayNetworking.send(new VikingActionPayload(targetId, action));
		} else {
			ClientPlayNetworking.send(new WheelCommandPayload(index));
		}
	}

	/** The command wheel stays open while its key is held; the hire wheel while right click is held. */
	private boolean isHoldKeyDown() {
		KeyMapping mapping = mode == Mode.COMMAND ? ValhallaCommandClient.WHEEL_KEY : this.minecraft.options.keyUse;
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		long handle = this.minecraft.getWindow().handle();
		int code = key.getValue();
		if (code < 0) return false;
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
		}
		return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
	}

	// ---- Easing & colour helpers ----

	private static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

	private static float easeOutBack(float t) {
		float c1 = 1.70158f, c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}

	private static float easeInCubic(float t) { return t * t * t; }

	private static int withAlpha(int argb, float mul) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(mul));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static int lerpColor(int c1, int c2, float t) {
		int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
		int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
