package com.valhalla.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.valhalla.command.entity.ModEntities;
import com.valhalla.command.entity.VikingEntity;
import com.valhalla.command.network.Orders;
import com.valhalla.command.network.WheelCommandPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValhallaCommand implements ModInitializer {
	public static final String MOD_ID = "valhallacommand";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.initialize();

		PayloadTypeRegistry.serverboundPlay().register(WheelCommandPayload.TYPE, WheelCommandPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WheelCommandPayload.TYPE,
				(payload, context) -> context.player().level().getServer().execute(
						() -> Orders.handle(context.player(), payload.command())));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("valhalla")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.literal("spawn")
								.executes(ctx -> spawn(ctx.getSource(), 1 + (int) (Math.random() * VikingEntity.SKIN_COUNT)))
								.then(Commands.argument("skin", IntegerArgumentType.integer(1, VikingEntity.SKIN_COUNT))
										.executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "skin")))))));

		LOGGER.info("Valhalla Command loaded. Skål!");
	}

	private static int spawn(CommandSourceStack source, int skin) {
		ServerLevel level = source.getLevel();
		VikingEntity viking = new VikingEntity(ModEntities.VIKING, level);
		viking.snapTo(source.getPosition());
		viking.setSkin(skin - 1);
		viking.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		viking.setPersistenceRequired();
		ServerPlayer player = source.getPlayer();
		if (player != null) viking.setOwner(player.getUUID());
		level.addFreshEntity(viking);
		source.sendSuccess(() -> Component.literal("Summoned Viking #" + skin + "."), true);
		return 1;
	}
}
