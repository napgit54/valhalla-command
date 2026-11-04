package com.valhalla.command.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.valhalla.command.ValhallaCommand;
import com.valhalla.command.client.render.VikingRenderer;
import com.valhalla.command.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;

public class ValhallaCommandClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "main"));

	/** Changeable in Options > Controls > Key Binds > Valhalla Command. Default: R. */
	public static final KeyMapping WHEEL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.valhallacommand.command_wheel",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_R,
			CATEGORY));

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.VIKING, VikingRenderer::new);
		AttackTracker.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (WHEEL_KEY.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new CommandWheelScreen());
				}
			}
		});
	}
}
