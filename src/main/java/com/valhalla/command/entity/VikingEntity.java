package com.valhalla.command.entity;

import com.valhalla.command.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class VikingEntity extends PathfinderMob {
	public static final int SKIN_COUNT = 20;
	/** Skins 11-20 (index 10+) are shieldmaidens. */
	public static final int FIRST_FEMALE_SKIN = 10;
	public static final int MAX_LEVEL = 35;

	private static final EntityDataAccessor<Integer> SKIN =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> HIRED =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> LEVEL =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PRICE =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> OWNER =
			SynchedEntityData.defineId(VikingEntity.class, EntityDataSerializers.STRING);

	/** Wheel actions on a Viking (sent from the client). */
	public static final int ACTION_HIRE = 0, ACTION_KICK = 1, ACTION_AID = 2;
	/** Which wheel to open when right-clicked: set by the client. (viking, isMine) */
	public static BiConsumer<VikingEntity, Boolean> openWheel = (v, mine) -> {};

	// One horn per "village" (64x64 block area) per minute
	private static final Map<Long, Long> HORN_COOLDOWNS = new HashMap<>();
	private static final int HORN_COOLDOWN_TICKS = 20 * 60;
	private static final int HORN_TICKS = 50;

	private @Nullable UUID owner;
	private boolean following;
	private int xp;

	// Temporary actions (server only)
	private int hornTicks;
	private int lastHurtSeen = -1;
	private int lastAttackSeen = -1;
	private boolean eating;
	private ItemStack savedMainHand = ItemStack.EMPTY;
	private ItemStack savedOffHand = ItemStack.EMPTY;
	private final float formationAngle;

	// Move orders (Charge / Retreat)
	private @Nullable Vec3 orderDest;
	private boolean orderRetreat;
	private int orderTicks;

	// Hold / Defend a position
	private @Nullable Vec3 holdPos;
	private boolean defending;

	// Raven's Eye: marked targets and/or a marked area
	private final List<Integer> ravenTargets = new ArrayList<>();
	private @Nullable Vec3 ravenArea;
	private int ravenTicks;
	private boolean ravenMarched;

	public VikingEntity(EntityType<? extends VikingEntity> type, Level level) {
		super(type, level);
		this.formationAngle = (float) (this.getRandom().nextFloat() * Math.PI * 2);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 22)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.ATTACK_DAMAGE, 1.5)
				.add(Attributes.FOLLOW_RANGE, 16)
				.add(Attributes.FALL_DAMAGE_MULTIPLIER, 0); // Vikings never take fall damage
	}

	// =====================================================================
	// Spawning, hiring, levels
	// =====================================================================

	/** Makes a Viking for hire at the given spot. Does not add it to the world. */
	public static VikingEntity create(ServerLevel level, Vec3 pos, int skin, int vikingLevel) {
		VikingEntity v = new VikingEntity(ModEntities.VIKING, level);
		v.snapTo(pos);
		v.setSkin(skin);
		v.setVikingLevel(vikingLevel);
		v.rollPrice();
		v.setPersistenceRequired();
		return v;
	}

	/** Wild Vikings are mostly level 1-3. Higher ones are rare; the rest must be trained. */
	public static int randomLevel(RandomSource r) {
		return 1 + (int) (Math.pow(r.nextFloat(), 3.5) * 14);
	}

	private void rollPrice() {
		int lv = getVikingLevel();
		setPrice(6 + lv * 6 + this.getRandom().nextInt(lv * 2 + 1));
	}

	/** Makes this Viking serve the player, and gives them a name. */
	public void hire(ServerPlayer player) {
		this.owner = player.getUUID();
		this.entityData.set(OWNER, owner.toString());
		this.entityData.set(HIRED, true);
		this.following = true;
		this.setTarget(null);
		this.setCustomName(Component.literal(VikingNames.random(isFemale(), this.getRandom())));
		this.setCustomNameVisible(true);
	}

	/** Removes this Viking from their chief's team. */
	public void dismiss() {
		this.owner = null;
		this.entityData.set(OWNER, "");
		this.entityData.set(HIRED, false);
		this.following = false;
		this.setTarget(null);
		this.setCustomName(null);
		this.setCustomNameVisible(false);
		rollPrice();
	}

	public String displayName() {
		return (getCustomName() != null ? getCustomName().getString() : "Viking") + " (Lvl " + getVikingLevel() + ")";
	}

	/** Higher levels: more health, harder hits, faster, better gear. */
	public void setVikingLevel(int level) {
		level = Math.max(1, Math.min(MAX_LEVEL, level));
		float healthRatio = this.getHealth() / Math.max(1f, this.getMaxHealth());
		this.entityData.set(LEVEL, level);
		setBase(Attributes.MAX_HEALTH, 20 + level * 2);
		setBase(Attributes.ATTACK_DAMAGE, 1 + level * 0.15);
		setBase(Attributes.MOVEMENT_SPEED, 0.25 + level * 0.0025);
		setBase(Attributes.FOLLOW_RANGE, 12 + level);
		this.setHealth(Math.max(1f, this.getMaxHealth() * (this.tickCount == 0 ? 1f : healthRatio)));
		equipForLevel();
	}

	private void setBase(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
		AttributeInstance inst = this.getAttribute(attr);
		if (inst != null) inst.setBaseValue(value);
	}

	private void equipForLevel() {
		Item[] gear = VikingGear.forLevel(getVikingLevel());
		EquipmentSlot[] slots = { EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND };
		for (int i = 0; i < slots.length; i++) {
			this.setItemSlot(slots[i], gear[i] == null ? ItemStack.EMPTY : new ItemStack(gear[i]));
		}
	}

	/** Experience from kills. Hired Vikings train up this way. */
	public void addXp(int amount) {
		if (getVikingLevel() >= MAX_LEVEL) return;
		xp += amount;
		boolean leveled = false;
		while (getVikingLevel() < MAX_LEVEL && xp >= xpToNext(getVikingLevel())) {
			xp -= xpToNext(getVikingLevel());
			setVikingLevel(getVikingLevel() + 1);
			leveled = true;
		}
		if (leveled) {
			this.playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
			Player o = getOwnerPlayer();
			if (o instanceof ServerPlayer sp) {
				sp.sendSystemMessage(Component.literal((getCustomName() != null ? getCustomName().getString() : "A warrior")
						+ " reached level " + getVikingLevel() + "!").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	public static int xpToNext(int level) {
		return 5 + level * 3;
	}

	// =====================================================================
	// AI. Low levels are clumsy; high levels are sharp.
	// =====================================================================

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new RetreatGoal());
		this.goalSelector.addGoal(1, new MoveOrderGoal());
		this.goalSelector.addGoal(2, new VikingMeleeGoal());
		this.goalSelector.addGoal(3, new BarterGoal());
		this.goalSelector.addGoal(4, new FollowOwnerGoal());
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return !following && holdPos == null && orderDest == null && hornTicks == 0 && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, VikingEntity.class));
		this.targetSelector.addGoal(2, new RavenTargetGoal());
		this.targetSelector.addGoal(3, new DefendAreaGoal());
		this.targetSelector.addGoal(4, new GuardOwnerGoal());
		this.goalSelector.addGoal(4, new HoldPositionGoal());
	}

	/** Never turn on our own chief or fellow warriors on the same side. */
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		if (target instanceof VikingEntity other && other.isHired() == this.isHired()
				&& (!isHired() || other.getOwnerString().equals(getOwnerString()))) return;
		if (target instanceof Player p && owner != null && owner.equals(p.getUUID())) return;
		if (target != null && isRetreating()) return;
		super.setTarget(target);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) return;

		// Swing the weapon like a player whenever we land a hit
		if (this.getLastHurtMobTimestamp() != lastAttackSeen) {
			lastAttackSeen = this.getLastHurtMobTimestamp();
			if (this.tickCount > 1) this.swing(InteractionHand.MAIN_HAND);
		}

		// Attacked by a player while unhired: fight, or blow the horn for friends
		if (this.getLastHurtByMobTimestamp() != lastHurtSeen) {
			lastHurtSeen = this.getLastHurtByMobTimestamp();
			if (!isHired() && this.getLastHurtByMob() instanceof Player attacker && this.tickCount > 1) {
				onAttackedByPlayer(attacker);
			}
		}

		// Horn being blown
		if (hornTicks > 0) {
			this.getNavigation().stop();
			if (--hornTicks == 0) {
				this.stopUsingItem();
				this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
				savedMainHand = ItemStack.EMPTY;
			}
		}

		// Finished eating a golden apple
		if (eating && !this.isUsingItem()) {
			eating = false;
			this.heal(6f);
			this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
			savedMainHand = ItemStack.EMPTY;
		}
	}

	private void onAttackedByPlayer(Player attacker) {
		if (!(this.level() instanceof ServerLevel level)) return;
		List<VikingEntity> friends = level.getEntitiesOfClass(VikingEntity.class, this.getBoundingBox().inflate(24),
				v -> v != this && v.isAlive() && !v.isHired());
		if (friends.isEmpty() || hornTicks > 0 || eating) return; // fight alone

		long key = (((long) this.blockPosition().getX() >> 6) << 32) ^ ((long) this.blockPosition().getZ() >> 6);
		long now = level.getGameTime();
		Long last = HORN_COOLDOWNS.get(key);
		if (last != null && now - last < HORN_COOLDOWN_TICKS) return;
		HORN_COOLDOWNS.put(key, now);

		// Blow the horn
		savedMainHand = this.getMainHandItem().copy();
		this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOAT_HORN));
		this.startUsingItem(InteractionHand.MAIN_HAND);
		hornTicks = HORN_TICKS;
		this.playSound(SoundEvents.RAID_HORN.value(), 6f, 1.1f);
		this.getLookControl().setLookAt(attacker, 30f, 30f);

		// Friends come running
		for (VikingEntity f : friends) f.setTarget(attacker);
	}

	/** Called when the chief gives a golden apple. */
	public boolean startAid() {
		if (eating || hornTicks > 0) return false;
		savedMainHand = this.getMainHandItem().copy();
		this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
		this.startUsingItem(InteractionHand.MAIN_HAND);
		eating = true;
		return true;
	}

	public boolean isBlowingHorn() { return this.isUsingItem() && this.getMainHandItem().is(Items.GOAT_HORN); }

	// =====================================================================
	// Right click
	// =====================================================================

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
		boolean mine = isHired() && getOwnerString().equals(player.getUUID().toString());
		if (isHired() && !mine) return super.mobInteract(player, hand);
		if (this.getTarget() == player) return super.mobInteract(player, hand); // angry at you
		if (this.level().isClientSide()) openWheel.accept(this, mine);
		return InteractionResult.SUCCESS;
	}

	// =====================================================================
	// Data
	// =====================================================================

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, 0);
		builder.define(HIRED, false);
		builder.define(LEVEL, 1);
		builder.define(PRICE, 10);
		builder.define(OWNER, "");
	}

	public int getSkin() { return Math.floorMod(this.entityData.get(SKIN), SKIN_COUNT); }
	public void setSkin(int skin) { this.entityData.set(SKIN, Math.floorMod(skin, SKIN_COUNT)); }
	public boolean isFemale() { return getSkin() >= FIRST_FEMALE_SKIN; }
	public boolean isHired() { return this.entityData.get(HIRED); }
	public int getVikingLevel() { return this.entityData.get(LEVEL); }
	public int getPrice() { return this.entityData.get(PRICE); }
	public void setPrice(int price) { this.entityData.set(PRICE, Math.max(1, price)); }
	/** The owner's UUID as text ("" if none). Synced, so the client can tell whose Viking this is. */
	public String getOwnerString() { return this.entityData.get(OWNER); }

	public boolean isOwnedBy(Player player) { return owner != null && owner.equals(player.getUUID()); }
	public boolean isFollowing() { return following; }
	public void setFollowing(boolean following) {
		this.following = following;
		if (following) { orderDest = null; holdPos = null; }
	}

	/** Hold this spot. Defend = also attack monsters that come near it. */
	public void orderHold(boolean defend) {
		this.following = false;
		this.orderDest = null;
		this.holdPos = this.position();
		this.defending = defend;
	}

	/** Raven's Eye orders: hunt these entities, and/or clear monsters around this spot. */
	public void setRavenOrders(List<Integer> targets, @Nullable Vec3 area) {
		this.ravenTargets.clear();
		this.ravenTargets.addAll(targets);
		this.ravenArea = area;
		this.ravenTicks = 20 * 120;
		this.ravenMarched = false;
		this.holdPos = null;
		this.orderDest = null;
		this.following = false;
		this.setTarget(null);
	}

	/** Charge (fight on the way if attacked) or retreat (ignore enemies) to a spot. */
	public void orderMove(Vec3 dest, boolean retreat) {
		this.following = false;
		this.orderDest = dest;
		this.orderRetreat = retreat;
		this.orderTicks = 20 * 40;
		this.holdPos = null;
		if (retreat) this.setTarget(null);
	}

	public boolean isRetreating() { return orderDest != null && orderRetreat; }

	public @Nullable Player getOwnerPlayer() {
		return owner == null ? null : this.level().getPlayerByUUID(owner);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		// Don't save a horn or half-eaten apple in hand
		if (!savedMainHand.isEmpty()) {
			this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
		}
		out.putInt("skin", getSkin());
		out.putInt("hired", isHired() ? 1 : 0);
		out.putInt("viking_level", getVikingLevel());
		out.putInt("price", getPrice());
		out.putInt("following", following ? 1 : 0);
		out.putInt("xp", xp);
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
		xp = in.getInt("xp").orElse(0);
		owner = in.getString("owner").map(UUID::fromString).orElse(null);
		this.entityData.set(OWNER, owner == null ? "" : owner.toString());
	}

	// =====================================================================
	// Goals
	// =====================================================================

	/** Melee that gets better with level: faster swings, and low levels sometimes lose interest. */
	private class VikingMeleeGoal extends MeleeAttackGoal {
		VikingMeleeGoal() {
			super(VikingEntity.this, 1.2, true);
		}

		@Override
		public boolean canUse() {
			return hornTicks == 0 && !eating && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			if (hornTicks > 0 || eating) return false;
			// Clumsy low-level fighters sometimes just give up
			int lv = getVikingLevel();
			if (lv < 6 && getRandom().nextInt(120 + lv * 40) == 0) {
				setTarget(null);
				return false;
			}
			return super.canContinueToUse();
		}

		@Override
		protected int getAttackInterval() {
			return this.adjustedTickDelay(Math.max(9, 30 - getVikingLevel() * 3 / 5));
		}
	}

	/** Level 10+: attack whatever hurts the chief. Level 20+: also attack whatever the chief attacks. */
	private class GuardOwnerGoal extends Goal {
		private @Nullable LivingEntity pick;

		GuardOwnerGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (!isHired() || getVikingLevel() < 10 || getTarget() != null) return false;
			Player o = getOwnerPlayer();
			if (o == null) return false;
			LivingEntity threat = o.getLastHurtByMob();
			if (threat != null && threat.isAlive() && o.tickCount - o.getLastHurtByMobTimestamp() < 100 && !(threat instanceof VikingEntity)) {
				pick = threat;
				return true;
			}
			if (getVikingLevel() >= 20) {
				LivingEntity victim = o.getLastHurtMob();
				if (victim != null && victim.isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 100 && !(victim instanceof VikingEntity)) {
					pick = victim;
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			setTarget(pick);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}
	}

	/** Raven's Eye: go after marked targets, then clear the marked area of monsters. */
	private class RavenTargetGoal extends Goal {
		private @Nullable LivingEntity pick;

		RavenTargetGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (ravenTicks <= 0) return false;
			ravenTicks--;
			if (isRetreating() || (getTarget() != null && getTarget().isAlive())) return false;
			pick = null;
			double best = Double.MAX_VALUE;
			ravenTargets.removeIf(id -> !(level().getEntity(id) instanceof LivingEntity e) || !e.isAlive());
			for (int id : ravenTargets) {
				if (level().getEntity(id) instanceof LivingEntity e) {
					double d = distanceToSqr(e);
					if (d < best) { best = d; pick = e; }
				}
			}
			if (pick == null && ravenArea != null) {
				Vec3 a = ravenArea;
				for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
						new net.minecraft.world.phys.AABB(a, a).inflate(12),
						e -> e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER)) {
					double d = distanceToSqr(e);
					if (d < best) { best = d; pick = e; }
				}
				if (pick == null && !ravenMarched) {
					ravenMarched = true;
					Vec3 keep = ravenArea;
					orderMove(keep, false); // march to the area
					ravenTicks = Math.max(ravenTicks, 20 * 60);
				}
			}
			if (pick == null && ravenTargets.isEmpty() && (ravenArea == null || ravenMarched && orderDest == null)) {
				ravenTicks = 0;
				ravenArea = null;
			}
			return pick != null;
		}

		@Override
		public void start() {
			setTarget(pick);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}
	}

	/** Defend order: attack monsters near the held spot. */
	private class DefendAreaGoal extends Goal {
		private @Nullable LivingEntity pick;

		DefendAreaGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (holdPos == null || !defending || getTarget() != null || tickCount % 10 != 0) return false;
			Vec3 h = holdPos;
			pick = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class,
					new net.minecraft.world.phys.AABB(h, h).inflate(12),
					e -> e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER)) {
				double d = distanceToSqr(e);
				if (d < best) { best = d; pick = e; }
			}
			return pick != null;
		}

		@Override
		public void start() {
			setTarget(pick);
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}
	}

	/** Hold / Defend: walk back to the spot when pushed or after a fight. */
	private class HoldPositionGoal extends Goal {
		HoldPositionGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return holdPos != null && getTarget() == null && orderDest == null && distanceToSqr(holdPos) > 3 * 3;
		}

		@Override
		public boolean canContinueToUse() {
			return holdPos != null && getTarget() == null && distanceToSqr(holdPos) > 1.5 * 1.5;
		}

		@Override
		public void start() {
			if (holdPos != null) getNavigation().moveTo(holdPos.x, holdPos.y, holdPos.z, 1.1);
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** Walks to an ordered spot in steps, so it works over long distances. */
	private class MoveOrderGoal extends Goal {
		private int repath;

		MoveOrderGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return orderDest != null && hornTicks == 0 && (orderRetreat || getTarget() == null);
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void tick() {
			if (orderDest == null) return;
			if (--orderTicks <= 0) { orderDest = null; return; }
			double dx = orderDest.x - getX(), dz = orderDest.z - getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < 2.5) { orderDest = null; getNavigation().stop(); return; }
			if (--repath <= 0 || getNavigation().isDone()) {
				repath = 20;
				double step = Math.min(dist, 14);
				int x = (int) Math.floor(getX() + dx / dist * step);
				int z = (int) Math.floor(getZ() + dz / dist * step);
				int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				getNavigation().moveTo(x + 0.5, y, z + 0.5, orderRetreat ? 1.5 : 1.45);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** Level 25+: fall back to the chief when badly hurt. */
	private class RetreatGoal extends Goal {
		RetreatGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Player o = getOwnerPlayer();
			return isHired() && getVikingLevel() >= 25 && o != null && !eating
					&& getHealth() < getMaxHealth() * 0.3f && distanceToSqr(o) > 9;
		}

		@Override
		public void tick() {
			Player o = getOwnerPlayer();
			if (o != null) getNavigation().moveTo(o, 1.35);
		}
	}

	/** Follow the chief at a comfortable distance, spread out around them. */
	private class FollowOwnerGoal extends Goal {
		private int repath;

		FollowOwnerGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Player o = getOwnerPlayer();
			return following && getTarget() == null && hornTicks == 0 && o != null && distanceToSqr(o) > 10 * 10;
		}

		@Override
		public boolean canContinueToUse() {
			Player o = getOwnerPlayer();
			return following && getTarget() == null && o != null && distanceToSqr(o) > 5 * 5;
		}

		@Override
		public void start() {
			repath = 0;
		}

		@Override
		public void tick() {
			Player o = getOwnerPlayer();
			if (o == null) return;
			double d2 = distanceToSqr(o);
			if (d2 > 32 * 32) {
				getNavigation().stop();
				snapTo(o.position());
				return;
			}
			if (--repath <= 0) {
				repath = 10;
				// Each warrior has their own spot around the chief
				double r = 3.5;
				double x = o.getX() + Math.cos(formationAngle) * r;
				double z = o.getZ() + Math.sin(formationAngle) * r;
				getNavigation().moveTo(x, o.getY(), z, d2 > 16 * 16 ? 1.3 : 1.0);
			}
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}
	}

	/** Unhired, calm Vikings go for dropped hacksilver, admire it, then trade something for it. */
	private class BarterGoal extends Goal {
		private @Nullable ItemEntity item;
		private int admire;
		private int cooldown;

		BarterGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private boolean calm() {
			return !isHired() && getTarget() == null && hornTicks == 0 && !eating;
		}

		@Override
		public boolean canUse() {
			if (!calm()) return false;
			if (cooldown > 0) { cooldown--; return false; }
			if (tickCount % 10 != 0) return false;
			List<ItemEntity> found = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(12),
					e -> e.isAlive() && e.getItem().is(ModItems.HACKSILVER));
			item = null;
			double best = Double.MAX_VALUE;
			for (ItemEntity e : found) {
				double d = distanceToSqr(e);
				if (d < best) { best = d; item = e; }
			}
			return item != null;
		}

		@Override
		public boolean canContinueToUse() {
			if (!calm() && admire == 0) return false;
			return admire > 0 || (item != null && item.isAlive());
		}

		@Override
		public void tick() {
			if (admire > 0) {
				getNavigation().stop();
				// Look down at the silver in hand
				getLookControl().setLookAt(getX() + getLookAngle().x, getEyeY() - 1.2, getZ() + getLookAngle().z);
				if (--admire == 0) finishBarter();
				return;
			}
			if (item == null || !item.isAlive()) return;
			getLookControl().setLookAt(item, 30f, 30f);
			if (distanceToSqr(item) > 1.8 * 1.8) {
				getNavigation().moveTo(item, 1.1);
				return;
			}
			// Pick up one piece
			ItemStack stack = item.getItem();
			stack.shrink(1);
			if (stack.isEmpty()) item.discard();
			else item.setItem(stack);
			savedOffHand = getOffhandItem().copy();
			setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.HACKSILVER));
			playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
			admire = 60;
		}

		private void finishBarter() {
			setItemInHand(InteractionHand.OFF_HAND, savedOffHand);
			savedOffHand = ItemStack.EMPTY;
			ItemStack loot = VikingGear.rollBarter(getRandom());
			Player near = level().getNearestPlayer(VikingEntity.this, 10);
			ItemEntity drop = new ItemEntity(level(), getX(), getEyeY() - 0.3, getZ(), loot);
			Vec3 dir = near != null ? near.position().subtract(position()).normalize().scale(0.3) : getLookAngle().scale(0.3);
			drop.setDeltaMovement(dir.x, 0.25, dir.z);
			level().addFreshEntity(drop);
			cooldown = 20;
		}

		@Override
		public void stop() {
			if (admire > 0) { admire = 0; finishBarter(); }
			item = null;
		}
	}
}
