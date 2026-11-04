package com.valhalla.command.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;

public class VikingEntity extends PathfinderMob {
	public static final int SKIN_COUNT = 20;
	/** Skins 11-20 (index 10+) are shieldmaidens. */
	public static final int FIRST_FEMALE_SKIN = 10;
	public static final int MAX_LEVEL = 5;

	private static final EntityDataAccessor<Integer> SKIN =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> HIRED =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> LEVEL =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PRICE =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);

	/** Set by the client to open the hire wheel. Does nothing on a dedicated server. */
	public static Consumer<VikingEntity> openHireScreen = v -> {};

	private @Nullable UUID owner;
	private boolean following;

	public VikingEntity(EntityType<? extends VikingEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 25)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4)
				.add(Attributes.FOLLOW_RANGE, 32);
	}

	// ---- Spawning ----

	/** Makes a Viking for hire at the given spot. Does not add it to the world. */
	public static VikingEntity create(ServerLevel level, Vec3 pos, int skin, int vikingLevel) {
		VikingEntity v = new VikingEntity(ModEntities.VIKING, level);
		v.snapTo(pos);
		v.setSkin(skin);
		v.setVikingLevel(vikingLevel);
		RandomSource r = level.getRandom();
		v.setPrice(vikingLevel * 8 + r.nextInt(9));
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		v.setPersistenceRequired();
		return v;
	}

	/** Mostly low levels, rarely high ones. */
	public static int randomLevel(RandomSource r) {
		int roll = r.nextInt(100);
		if (roll < 40) return 1;
		if (roll < 70) return 2;
		if (roll < 87) return 3;
		if (roll < 96) return 4;
		return 5;
	}

	/** Makes this Viking serve the player, and gives them a name. */
	public void hire(ServerPlayer player) {
		this.owner = player.getUUID();
		this.entityData.set(HIRED, true);
		this.following = true;
		String name = VikingNames.random(isFemale(), this.getRandom());
		this.setCustomName(Component.literal(name));
		this.setCustomNameVisible(true);
		this.setPersistenceRequired();
	}

	// ---- AI ----

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

	// ---- Right click: hire wheel ----

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || isHired()) return super.mobInteract(player, hand);
		if (this.level().isClientSide()) openHireScreen.accept(this);
		return InteractionResult.SUCCESS;
	}

	// ---- Data ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, 0);
		builder.define(HIRED, false);
		builder.define(LEVEL, 1);
		builder.define(PRICE, 10);
	}

	public int getSkin() { return Math.floorMod(this.entityData.get(SKIN), SKIN_COUNT); }
	public void setSkin(int skin) { this.entityData.set(SKIN, Math.floorMod(skin, SKIN_COUNT)); }
	public boolean isFemale() { return getSkin() >= FIRST_FEMALE_SKIN; }
	public boolean isHired() { return this.entityData.get(HIRED); }
	public int getVikingLevel() { return this.entityData.get(LEVEL); }
	public int getPrice() { return this.entityData.get(PRICE); }
	public void setPrice(int price) { this.entityData.set(PRICE, Math.max(1, price)); }

	/** Higher levels have more health and hit harder. */
	public void setVikingLevel(int level) {
		level = Math.max(1, Math.min(MAX_LEVEL, level));
		this.entityData.set(LEVEL, level);
		AttributeInstance hp = this.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) hp.setBaseValue(20 + 5 * level);
		AttributeInstance dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmg != null) dmg.setBaseValue(3 + level);
		this.setHealth(this.getMaxHealth());
	}

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
		out.putInt("hired", isHired() ? 1 : 0);
		out.putInt("viking_level", getVikingLevel());
		out.putInt("price", getPrice());
		out.putInt("following", following ? 1 : 0);
		if (owner != null) out.putString("owner", owner.toString());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		setSkin(in.getInt("skin").orElse(0));
		this.entityData.set(HIRED, in.getInt("hired").orElse(0) == 1);
		this.entityData.set(LEVEL, Math.max(1, Math.min(MAX_LEVEL, in.getInt("viking_level").orElse(1))));
		setPrice(in.getInt("price").orElse(10));
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
