package com.valhalla.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.valhalla.command.ValhallaCommand;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * The radial command wheel. Opens while the wheel key is held, closes on release.
 * Each section is its own texture (cut from the wheel artwork) so it can animate on its own.
 */
public class CommandWheelScreen extends Screen {
	// ---- Commands, clockwise from the top ----
	public static final String[] COMMANDS = {
			"Follow", "Charge", "Attack", "Defend", "Hold", "Retreat"
	};
	private static final ItemStack[] ICONS = {
			new ItemStack(Items.LEAD),          // Follow
			new ItemStack(Items.IRON_AXE),      // Charge
			new ItemStack(Items.IRON_SWORD),    // Attack
			new ItemStack(Items.SHIELD),        // Defend
			new ItemStack(Items.ANVIL),         // Hold
			new ItemStack(Items.LEATHER_BOOTS)  // Retreat
	};
	private static final int SEGMENTS = COMMANDS.length;

	// ---- Textures (512x512, all aligned to the same canvas) ----
	private static final int TEX = 512;
	private static final Identifier[] SEG_TEX = new Identifier[SEGMENTS];
	private static final Identifier[] SEG_HOVER_TEX = new Identifier[SEGMENTS];
	private static final Identifier CENTER_TEX = tex("center");
	static {
		for (int i = 0; i < SEGMENTS; i++) {
			SEG_TEX[i] = tex("seg" + i);
			SEG_HOVER_TEX[i] = tex("seg" + i + "_hover");
		}
	}

	// ---- Look & feel (sizes in GUI units) ----
	private static final float WHEEL_SIZE = 190f;    // full diameter of the artwork
	private static final float INNER_RADIUS = 30f;   // inside this = centre (no selection)
	private static final float LABEL_RADIUS = 0.33f; // where icons sit, as a fraction of WHEEL_SIZE
	private static final float HOVER_PUSH = 7f;
	private static final float HOVER_GROW = 0.04f;
	private static final int COLOR_TEXT = 0xFFFFFFFF;

	// ---- Animation timings (ms) ----
	private static final float OPEN_MS = 220f;
	private static final float CLOSE_MS = 150f;
	private static final float STAGGER_MS = 28f;
	private static final float HOVER_SPEED = 14f;

	private final long openedAt = System.nanoTime();
	private long closingAt = -1;
	private long lastFrame = System.nanoTime();
	private final float[] hover = new float[SEGMENTS];
	private int hovered = -1;

	public CommandWheelScreen() {
		super(Component.literal("Command Wheel"));
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

		if (closingAt < 0 && !isWheelKeyHeld()) {
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

			pose.pushMatrix();
			pose.translate((float) Math.cos(mid) * push, (float) Math.sin(mid) * push);
			pose.scale(grow, grow);

			// Normal section, with the brighter hover version faded in on top
			drawTex(graphics, SEG_TEX[i], half, size, withAlpha(0xFFFFFFFF, alpha));
			if (h > 0.01f) drawTex(graphics, SEG_HOVER_TEX[i], half, size, withAlpha(0xFFFFFFFF, alpha * h));

			// Icon with its name underneath, in the Minecraft font
			if (alpha > 0.6f) {
				float labelR = LABEL_RADIUS * size;
				float iconScale = scale * (1f + 0.15f * h);
				pose.translate((float) Math.cos(mid) * labelR, (float) Math.sin(mid) * labelR);
				pose.scale(iconScale, iconScale);
				graphics.fakeItem(ICONS[i], -8, -13);
				String label = COMMANDS[i];
				int tw = this.font.width(label);
				int textColor = withAlpha(lerpColor(COLOR_TEXT, 0xFFFFF3C4, h), alpha);
				graphics.text(this.font, label, -tw / 2, 5, textColor, true);
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

	/** Which section the mouse points at, or -1 for the centre. */
	private int pickSegment(float x, float y) {
		if (x * x + y * y < INNER_RADIUS * INNER_RADIUS) return -1;
		double step = Math.PI * 2 / SEGMENTS;
		double a = Math.atan2(y, x) + Math.PI / 2 + step / 2; // 0 = top, clockwise
		a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
		return (int) (a / step) % SEGMENTS;
	}

	/** Called when the wheel closes. -1 means nothing was picked. */
	private void onSelect(int index) {
		if (index < 0) return;
		// Placeholder until the army exists: show the order above the hotbar.
		this.minecraft.gui.hud.setOverlayMessage(Component.literal("Order: " + COMMANDS[index]), false);
	}

	private boolean isWheelKeyHeld() {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(ValhallaCommandClient.WHEEL_KEY);
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
