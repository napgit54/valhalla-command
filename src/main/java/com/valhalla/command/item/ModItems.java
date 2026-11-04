package com.valhalla.command.item;

import com.valhalla.command.ValhallaCommand;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ModItems {
	public static final ResourceKey<Item> HACKSILVER_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "hacksilver"));

	/** Viking currency. 99 is the largest stack size Minecraft allows. */
	public static final Item HACKSILVER = Registry.register(BuiltInRegistries.ITEM, HACKSILVER_KEY,
			new Item(new Item.Properties().stacksTo(99).setId(HACKSILVER_KEY)));

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
				.register(tab -> tab.accept(HACKSILVER));
	}

	/** How much hacksilver the player is carrying. */
	public static int countHacksilver(Player player) {
		Inventory inv = player.getInventory();
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(HACKSILVER)) total += stack.getCount();
		}
		return total;
	}

	/** Takes hacksilver from the player. Returns false (and takes nothing) if they can't afford it. */
	public static boolean takeHacksilver(Player player, int amount) {
		if (countHacksilver(player) < amount) return false;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(HACKSILVER)) {
				int take = Math.min(amount, stack.getCount());
				stack.shrink(take);
				amount -= take;
			}
		}
		inv.setChanged();
		return true;
	}
}
