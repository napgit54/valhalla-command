package com.valhalla.command.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class VikingEntity extends PathfinderMob {
	public static final int SKIN_COUNT = 20;
	private static final EntityDataAccessor<Integer> SKIN =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);

	private @Nullable UUID owner;
	private boolean following;

	public VikingEntity(EntityType<? extends VikingEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 5)
				.add(Attributes.FOLLOW_RANGE, 32);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(2, new FollowOwnerGoal());
		this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return !following && super.canUse();
			}
		});
		this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VikingEntity.class));
	}

	// Never turn on our own chief or fellow warriors
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof VikingEntity) return;
		if (target instanceof Player p && owner != null && owner.equals(p.getUUID())) return;
		super.setTarget(target);
	}

	// ---- Data ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, 0);
	}

	public int getSkin() {
		return Math.floorMod(this.entityData.get(SKIN), SKIN_COUNT);
	}

	public void setSkin(int skin) {
		this.entityData.set(SKIN, Math.floorMod(skin, SKIN_COUNT));
	}

	public void setOwner(@Nullable UUID owner) { this.owner = owner; }
	public boolean isOwnedBy(Player player) { return owner != null && owner.equals(player.getUUID()); }
	public boolean isFollowing() { return following; }
	public void setFollowing(boolean following) { this.following = following; }

	public @Nullable Player getOwnerPlayer() {
		return owner == null ? null : this.level().getPlayerByUUID(owner);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("skin", getSkin());
		out.putInt("following", following ? 1 : 0);
		if (owner != null) out.putString("owner", owner.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		setSkin(in.getInt("skin").orElse(0));
		following = in.getInt("following").orElse(0) == 1;
		owner = in.getString("owner").map(UUID::fromString).orElse(null);
	}

	// ---- Follow the chief ----

	private class FollowOwnerGoal extends Goal {
		FollowOwnerGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			Player o = getOwnerPlayer();
			return following && getTarget() == null && o != null && distanceToSqr(o) > 16;
		}

		@Override
		public boolean canContinueToUse() {
			Player o = getOwnerPlayer();
			return following && getTarget() == null && o != null && distanceToSqr(o) > 6;
		}

		@Override
		public void tick() {
			Player o = getOwnerPlayer();
			if (o == null) return;
			getLookControl().setLookAt(o, 10f, 30f);
			if (distanceToSqr(o) > 24 * 24) {
				getNavigation().stop();
				snapTo(o.position());
			} else {
				getNavigation().moveTo(o, 1.2);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}
}
