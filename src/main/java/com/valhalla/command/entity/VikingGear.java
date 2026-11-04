package com.valhalla.command.entity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Gear by level, and the hacksilver barter loot pool. */
public final class VikingGear {
	private VikingGear() {}

	/** {weapon, head, chest, legs, feet, offhand}. null = nothing. */
	public static @Nullable Item[] forLevel(int level) {
		if (level >= 35) return new Item[] { Items.NETHERITE_AXE, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHIELD };
		if (level >= 30) return new Item[] { Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHIELD };
		if (level >= 25) return new Item[] { Items.DIAMOND_AXE, Items.IRON_HELMET, Items.DIAMOND_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHIELD };
		if (level >= 20) return new Item[] { Items.IRON_AXE, Items.CHAINMAIL_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHIELD };
		if (level >= 15) return new Item[] { Items.IRON_AXE, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, Items.SHIELD };
		if (level >= 12) return new Item[] { Items.IRON_AXE, null, Items.LEATHER_CHESTPLATE, null, Items.LEATHER_BOOTS, Items.SHIELD };
		if (level >= 10) return new Item[] { Items.IRON_AXE, null, Items.LEATHER_CHESTPLATE, null, Items.LEATHER_BOOTS, null };
		if (level >= 5) return new Item[] { Items.STONE_AXE, null, Items.LEATHER_CHESTPLATE, null, null, null };
		return new Item[] { Items.WOODEN_AXE, null, null, null, null, null };
	}

	// ---- Barter loot: decent but not overpowered ----
	private record Loot(Item item, int min, int max, int weight) {}

	private static final Loot[] POOL = {
			new Loot(Items.ARROW, 6, 14, 20),
			new Loot(Items.BREAD, 2, 5, 20),
			new Loot(Items.COOKED_PORKCHOP, 1, 3, 14),
			new Loot(Items.IRON_NUGGET, 4, 9, 15),
			new Loot(Items.LEATHER, 2, 4, 12),
			new Loot(Items.STRING, 2, 5, 10),
			new Loot(Items.FLINT, 2, 4, 8),
			new Loot(Items.IRON_INGOT, 1, 2, 9),
			new Loot(Items.GOLD_NUGGET, 3, 7, 8),
			new Loot(Items.SHIELD, 1, 1, 3),
			new Loot(Items.IRON_AXE, 1, 1, 3),
			new Loot(Items.SADDLE, 1, 1, 2),
			new Loot(Items.EMERALD, 1, 2, 3),
			new Loot(Items.GOLDEN_APPLE, 1, 1, 1),
	};

	public static ItemStack rollBarter(RandomSource r) {
		int total = 0;
		for (Loot l : POOL) total += l.weight;
		int roll = r.nextInt(total);
		for (Loot l : POOL) {
			roll -= l.weight;
			if (roll < 0) return new ItemStack(l.item, l.min + r.nextInt(l.max - l.min + 1));
		}
		return new ItemStack(Items.BREAD);
	}
}
