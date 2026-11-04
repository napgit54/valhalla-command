package com.valhalla.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The radial command wheel. Opens while the wheel key is held, closes on release.
 * Everything is drawn from scanline rectangles at real pixel resolution, so it
 * stays crisp at every GUI scale.
 */
public class CommandWheelScreen extends Screen {
	// ---- Look & feel (all sizes in GUI units) ----
	public static final String[] COMMANDS = {
			"Follow", "Charge", "Attack", "Defend", "Hold", "Retreat", "Rally", "Spread"
	};
	private static final int SEGMENTS = COMMANDS.length;
	private static final float OUTER_RADIUS = 92f;
	private static final float INNER_RADIUS = 36f;
	private static final float CENTER_RADIUS = 30f;
	private static final float GAP = 3.5f;          // the "invisible line" between sections
	private static final float HOVER_PUSH = 7f;     // how far a hovered section slides out
	private static final float HOVER_GROW = 0.04f;  // how much a hovered section grows

	private static final int COLOR_SEGMENT = 0xF0EDEDED;
	private static final int COLOR_HOVER   = 0xFF5B8DB8; // icy steel blue
	private static final int COLOR_CENTER  = 0xF0EDEDED;
	private static final int COLOR_RING    = 0xFFEDEDED;
	private static final int COLOR_TEXT    = 0xFF1A1A1A;

	// ---- Animation timings (ms) ----
	private static final float OPEN_MS = 220f;
	private static final float CLOSE_MS = 150f;
	private static final float STAGGER_MS = 22f;
	private static final float HOVER_SPEED = 14f; // higher = snappier hover in/out

	private final long openedAt = System.nanoTime();
	private long closingAt = -1;
	private long lastFrame = System.nanoTime();
	private final float[] hover = new float[SEGMENTS];
	private int hovered = -1;

	// Cached pixel geometry: per segment, flat arrays of {y, x1, x2} runs
	private int[][] segmentRuns;
	private int[] centerRuns;
	private int[] ringRuns;
	private float builtScale = -1;

	public CommandWheelScreen() {
		super(Component.literal("Command Wheel"));
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

		float scale = (float) this.minecraft.getWindow().getWidth() / Math.max(1, this.width);
		if (scale != builtScale) buildGeometry(scale);

		// Close as soon as the key is let go
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

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1f / scale, 1f / scale);          // work in real pixels
		pose.translate(cx * scale, cy * scale);

		// ---- Segments ----
		double step = Math.PI * 2 / SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			float p = clamp01((openMs - i * STAGGER_MS) / OPEN_MS);
			float s = easeOutBack(p) * closeEase;
			float alpha = clamp01(p * 1.6f) * closeEase;
			if (alpha <= 0.01f || s <= 0.01f) continue;

			float h = hover[i];
			double mid = -Math.PI / 2 + i * step;
			float push = (HOVER_PUSH * h + (1f - closeEase) * 10f) * scale;
			float dx = (float) Math.cos(mid) * push;
			float dy = (float) Math.sin(mid) * push;
			float grow = s * (1f + HOVER_GROW * h);

			int color = withAlpha(lerpColor(COLOR_SEGMENT, COLOR_HOVER, h), alpha);

			pose.pushMatrix();
			pose.translate(dx, dy);
			pose.scale(grow, grow);
			drawRuns(graphics, segmentRuns[i], color);
			pose.popMatrix();
		}

		// ---- Centre circle ----
		float cp = clamp01(openMs / (OPEN_MS * 0.8f));
		float cs = easeOutBack(cp) * closeEase;
		float ca = clamp01(cp * 2f) * closeEase;
		if (cs > 0.01f) {
			pose.pushMatrix();
			pose.scale(cs, cs);
			drawRuns(graphics, centerRuns, withAlpha(COLOR_CENTER, ca));
			pose.popMatrix();
		}

		pose.popMatrix();
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

	// ---- Geometry ----

	private void buildGeometry(float scale) {
		builtScale = scale;
		float outer = OUTER_RADIUS * scale, inner = INNER_RADIUS * scale;
		float halfGap = GAP * scale / 2f;
		double step = Math.PI * 2 / SEGMENTS;

		List<List<Integer>> runs = new ArrayList<>();
		for (int i = 0; i < SEGMENTS; i++) runs.add(new ArrayList<>());

		int r = (int) Math.ceil(outer);
		for (int y = -r; y <= r; y++) {
			int runSeg = -1, runStart = 0;
			for (int x = -r; x <= r + 1; x++) {
				int seg = -1;
				if (x <= r) {
					float px = x + 0.5f, py = y + 0.5f;
					float d2 = px * px + py * py;
					if (d2 <= outer * outer && d2 >= inner * inner) {
						double a = Math.atan2(py, px) + Math.PI / 2 + step / 2;
						a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
						int s = (int) (a / step) % SEGMENTS;
						double local = a - s * step;               // 0..step inside the section
						double edge = Math.min(local, step - local); // angle to nearest border
						double dist = Math.sqrt(d2) * Math.sin(edge);
						if (dist >= halfGap) seg = s;
					}
				}
				if (seg != runSeg) {
					if (runSeg >= 0) {
						List<Integer> l = runs.get(runSeg);
						l.add(y); l.add(runStart); l.add(x);
					}
					runSeg = seg;
					runStart = x;
				}
			}
		}
		segmentRuns = new int[SEGMENTS][];
		for (int i = 0; i < SEGMENTS; i++) segmentRuns[i] = toArray(runs.get(i));
		centerRuns = discRuns(CENTER_RADIUS * scale, 0);
		ringRuns = discRuns(CENTER_RADIUS * scale, CENTER_RADIUS * scale - Math.max(1.5f, scale));
	}

	private static int[] discRuns(float outer, float inner) {
		List<Integer> l = new ArrayList<>();
		int r = (int) Math.ceil(outer);
		for (int y = -r; y <= r; y++) {
			float py = y + 0.5f;
			if (py * py > outer * outer) continue;
			int xo = (int) Math.floor(Math.sqrt(outer * outer - py * py));
			if (inner <= 0 || py * py >= inner * inner) {
				l.add(y); l.add(-xo); l.add(xo);
			} else {
				int xi = (int) Math.ceil(Math.sqrt(inner * inner - py * py));
				l.add(y); l.add(-xo); l.add(-xi);
				l.add(y); l.add(xi); l.add(xo);
			}
		}
		return toArray(l);
	}

	private static void drawRuns(GuiGraphicsExtractor g, int[] runs, int color) {
		for (int i = 0; i < runs.length; i += 3) {
			g.fill(runs[i + 1], runs[i], runs[i + 2], runs[i] + 1, color);
		}
	}

	private static int[] toArray(List<Integer> l) {
		int[] a = new int[l.size()];
		for (int i = 0; i < a.length; i++) a[i] = l.get(i);
		return a;
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
