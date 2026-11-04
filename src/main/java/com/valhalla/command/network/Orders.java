package com.valhalla.command.network;

import com.valhalla.command.entity.VikingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Carries out wheel commands on the server. Indexes match the wheel, clockwise from the top. */
public final class Orders {
	public static final int FOLLOW = 0, CHARGE = 1, ATTACK = 2, DEFEND = 3, HOLD = 4, RETREAT = 5;

	private Orders() {}

	public static void handle(ServerPlayer player, int command) {
		List<VikingEntity> army = player.level().getEntitiesOfClass(VikingEntity.class,
				player.getBoundingBox().inflate(64), v -> v.isAlive() && v.isOwnedBy(player));
		if (army.isEmpty()) {
			tell(player, "You have no warriors nearby.");
			return;
		}

		switch (command) {
			case FOLLOW -> {
				boolean anyIdle = army.stream().anyMatch(v -> !v.isFollowing());
				army.forEach(v -> v.setFollowing(anyIdle));
				tell(player, anyIdle ? "Your warriors follow you." : "Your warriors stand down.");
			}
			case ATTACK -> {
				LivingEntity target = player.getLastHurtMob();
				boolean recent = player.tickCount - player.getLastHurtMobTimestamp() < 200;
				if (target == null || !target.isAlive() || !recent || target instanceof VikingEntity) {
					tell(player, "Strike an enemy first, then order the attack.");
					return;
				}
				army.forEach(v -> v.setTarget(target));
				tell(player, "To battle! " + army.size() + " warriors attack.");
			}
			default -> tell(player, "That order is coming soon.");
		}
	}

	private static void tell(ServerPlayer player, String msg) {
		player.sendSystemMessage(Component.literal(msg), true);
	}
}
