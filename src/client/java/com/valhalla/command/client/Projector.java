package com.valhalla.command.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Turns world positions into screen positions (and back into a look direction). First person view. */
public final class Projector {
	private Projector() {}

	private static Vec3 cam;
	private static Vec3 fwd, right, up;
	private static double scale;
	private static int w, h;

	/** Call once per frame before project(). */
	public static void setup(float partial, int screenW, int screenH) {
		Minecraft mc = Minecraft.getInstance();
		cam = mc.player.getEyePosition(partial);
		double yaw = Math.toRadians(mc.player.getViewYRot(partial));
		double pitch = Math.toRadians(mc.player.getViewXRot(partial));
		fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		up = right.cross(fwd);
		double fov = Math.toRadians(mc.options.fov().get());
		w = screenW;
		h = screenH;
		scale = (h / 2.0) / Math.tan(fov / 2.0);
	}

	/** {x, y} in GUI coordinates, or null if behind the camera. */
	public static float @Nullable [] project(Vec3 world) {
		Vec3 rel = world.subtract(cam);
		double z = rel.dot(fwd);
		if (z < 0.2) return null;
		double x = rel.dot(right), y = rel.dot(up);
		return new float[] { (float) (w / 2.0 + x / z * scale), (float) (h / 2.0 - y / z * scale) };
	}

	/** World direction through a screen point. */
	public static Vec3 direction(double sx, double sy) {
		double x = (sx - w / 2.0) / scale, y = -(sy - h / 2.0) / scale;
		return fwd.add(right.scale(x)).add(up.scale(y)).normalize();
	}

	public static Vec3 camera() { return cam; }
}
