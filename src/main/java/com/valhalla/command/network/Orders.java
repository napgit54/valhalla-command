package com.valhalla.command.network;

import com.valhalla.command.entity.VikingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
				// Only warriors within 20 blocks hear the call
				List<VikingEntity> near = army.stream().filter(v -> v.distanceToSqr(player) <= 20 * 20).toList();
				if (near.isEmpty()) {
					tell(player, "No warriors within 20 blocks.");
					return;
				}
				boolean anyIdle = near.stream().anyMatch(v -> !v.isFollowing());
				near.forEach(v -> v.setFollowing(anyIdle));
				tell(player, anyIdle ? near.size() + " warriors follow you." : "Your warriors stand down.");
			}
			case CHARGE -> {
				// Charge to the crosshair, up to 50 blocks
				Vec3 dest = player.pick(50, 1f, false).getLocation();
				for (VikingEntity v : army) {
					double ox = (v.getRandom().nextDouble() - 0.5) * 4, oz = (v.getRandom().nextDouble() - 0.5) * 4;
					v.orderMove(dest.add(ox, 0, oz), false);
				}
				tell(player, "Charge!");
			}
			case RETREAT -> {
				// Run away from the fight (or from where you're looking), up to 80 blocks
				Vec3 center = Vec3.ZERO, threat = Vec3.ZERO;
				int threats = 0;
				for (VikingEntity v : army) {
					center = center.add(v.position());
					if (v.getTarget() != null) { threat = threat.add(v.getTarget().position()); threats++; }
				}
				center = center.scale(1.0 / army.size());
				Vec3 away = threats > 0
						? center.subtract(threat.scale(1.0 / threats))
						: player.getLookAngle().scale(-1);
				away = new Vec3(away.x, 0, away.z);
				if (away.lengthSqr() < 1.0E-4) away = player.getLookAngle().scale(-1);
				away = away.normalize().scale(80);
				for (VikingEntity v : army) {
					double ox = (v.getRandom().nextDouble() - 0.5) * 6, oz = (v.getRandom().nextDouble() - 0.5) * 6;
					v.orderMove(v.position().add(away).add(ox, 0, oz), true);
				}
				tell(player, "Fall back!");
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
			case HOLD -> {
				army.forEach(v -> v.orderHold(false));
				tell(player, "Hold this ground!");
			}
			case DEFEND -> {
				army.forEach(v -> v.orderHold(true));
				tell(player, "Defend this spot!");
			}
			default -> {}
		}
	}

	/** Raven's Eye: marked entities and/or a marked area. */
	public static void raven(ServerPlayer player, List<Integer> targets, boolean hasArea, Vec3 area) {
		List<VikingEntity> army = player.level().getEntitiesOfClass(VikingEntity.class,
				player.getBoundingBox().inflate(96), v -> v.isAlive() && v.isOwnedBy(player));
		if (army.isEmpty()) {
			tell(player, "You have no warriors nearby.");
			return;
		}
		// Never let the raven mark your own warband or yourself
		List<Integer> clean = targets.stream().filter(id -> {
			var e = player.level().getEntity(id);
			return e != null && e != player && e.distanceToSqr(player) <= 55 * 55
					&& !(e instanceof VikingEntity v && v.isOwnedBy(player));
		}).limit(32).toList();
		Vec3 a = hasArea && area.distanceToSqr(player.position()) <= 55 * 55 ? area : null;
		if (clean.isEmpty() && a == null) return;
		army.forEach(v -> v.setRavenOrders(clean, a));
		tell(player, "The ravens have spoken. " + army.size() + " warriors hunt.");
	}

	private static void tell(ServerPlayer player, String msg) {
		player.sendSystemMessage(Component.literal(msg), true);
	}
}
