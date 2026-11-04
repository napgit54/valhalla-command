package com.valhalla.command.client;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** Remembers what you last hit, so the wheel knows if you are in a fight. */
public final class AttackTracker {
	private static final long FIGHT_MS = 10_000;
	private static @Nullable Entity lastTarget;
	private static long lastHitAt;

	private AttackTracker() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				lastTarget = entity;
				lastHitAt = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean isAttacking() {
		return lastTarget != null && lastTarget.isAlive()
				&& System.currentTimeMillis() - lastHitAt < FIGHT_MS;
	}
}
