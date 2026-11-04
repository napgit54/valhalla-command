package com.valhalla.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.valhalla.command.entity.ModEntities;
import com.valhalla.command.entity.VikingEntity;
import com.valhalla.command.item.ModItems;
import com.valhalla.command.network.HirePayload;
import com.valhalla.command.network.Orders;
import com.valhalla.command.network.WheelCommandPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValhallaCommand implements ModInitializer {
	public static final String MOD_ID = "valhallacommand";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.initialize();
		ModItems.initialize();

		// ---- Packets ----
		PayloadTypeRegistry.serverboundPlay().register(WheelCommandPayload.TYPE, WheelCommandPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HirePayload.TYPE, HirePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(WheelCommandPayload.TYPE,
				(payload, context) -> context.player().level().getServer().execute(
						() -> Orders.handle(context.player(), payload.command())));
		ServerPlayNetworking.registerGlobalReceiver(HirePayload.TYPE,
				(payload, context) -> context.player().level().getServer().execute(
						() -> handleHire(context.player(), payload.entityId())));

		// ---- Vikings replace villagers ----
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType() == EntityType.VILLAGER) {
				level.getServer().execute(() -> replaceVillager(entity, level));
			}
		});

		// ---- Commands (op only) ----
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("valhalla")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						// /valhalla spawn [skin]  -> a Viking already in your service
						.then(Commands.literal("spawn")
								.executes(ctx -> spawn(ctx.getSource(), randomSkin(ctx.getSource()), 1, true))
								.then(Commands.argument("skin", IntegerArgumentType.integer(1, VikingEntity.SKIN_COUNT))
										.executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "skin"), 1, true))))
						// /valhalla forhire <skin> [level]  -> a Viking you can hire
						.then(Commands.literal("forhire")
								.then(Commands.argument("skin", IntegerArgumentType.integer(1, VikingEntity.SKIN_COUNT))
										.executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "skin"), 1, false))
										.then(Commands.argument("level", IntegerArgumentType.integer(1, VikingEntity.MAX_LEVEL))
												.executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "skin"),
														IntegerArgumentType.getInteger(ctx, "level"), false)))))));

		LOGGER.info("Valhalla Command loaded. Skål!");
	}

	private static int randomSkin(CommandSourceStack source) {
		return 1 + source.getLevel().getRandom().nextInt(VikingEntity.SKIN_COUNT);
	}

	private static int spawn(CommandSourceStack source, int skin, int level, boolean hired) {
		ServerLevel world = source.getLevel();
		VikingEntity viking = VikingEntity.create(world, source.getPosition(), skin - 1, level);
		ServerPlayer player = source.getPlayer();
		if (hired && player != null) viking.hire(player);
		world.addFreshEntity(viking);
		source.sendSuccess(() -> Component.literal(hired
				? "Summoned Viking #" + skin + " to your warband."
				: "Summoned Viking #" + skin + " (level " + level + ") for hire."), true);
		return 1;
	}

	private static void replaceVillager(Entity villager, ServerLevel level) {
		if (villager.isRemoved()) return;
		VikingEntity viking = VikingEntity.create(level, villager.position(),
				level.getRandom().nextInt(VikingEntity.SKIN_COUNT), VikingEntity.randomLevel(level.getRandom()));
		villager.discard();
		level.addFreshEntity(viking);
	}

	private static void handleHire(ServerPlayer player, int entityId) {
		if (!(player.level().getEntity(entityId) instanceof VikingEntity viking)) return;
		if (viking.isHired() || !viking.isAlive() || viking.distanceToSqr(player) > 10 * 10) return;
		int price = viking.getPrice();
		if (!ModItems.takeHacksilver(player, price)) {
			player.sendSystemMessage(Component.literal("You need " + price + " hacksilver to hire this warrior."), true);
			return;
		}
		viking.hire(player);
		player.sendSystemMessage(Component.literal(viking.getName().getString() + " has joined your warband!"), true);
	}
}
