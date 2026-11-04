package com.valhalla.command.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.valhalla.command.ValhallaCommand;
import com.valhalla.command.entity.VikingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityAttachment;

/** Draws Vikings with the normal player model and one of 20 skins, plus a price tag if they are for hire. */
public class VikingRenderer extends HumanoidMobRenderer<VikingEntity, VikingRenderState, HumanoidModel<VikingRenderState>> {
	/** The hacksilver icon, added to Minecraft's font (assets/minecraft/font/default.json). */
	public static final String HACKSILVER_GLYPH = "";
	private static final double TAG_RANGE_SQ = 16 * 16;

	private static final Identifier[] SKINS = new Identifier[VikingEntity.SKIN_COUNT];
	static {
		for (int i = 0; i < SKINS.length; i++) {
			SKINS[i] = Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "textures/entity/viking/viking_" + (i + 1) + ".png");
		}
	}

	public VikingRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public VikingRenderState createRenderState() {
		return new VikingRenderState();
	}

	@Override
	public void extractRenderState(VikingEntity entity, VikingRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.skin = entity.getSkin();
		state.hired = entity.isHired();
		state.price = entity.getPrice();
		state.vikingLevel = entity.getVikingLevel();
		if (state.nameTagAttachment == null) {
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
		}
	}

	@Override
	public void submit(VikingRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (state.hired || state.nameTagAttachment == null || state.distanceToCameraSq > TAG_RANGE_SQ) return;

		// Bottom line first, then move up for the price line
		Component levelLine = Component.literal("Level " + state.vikingLevel).withStyle(ChatFormatting.GRAY);
		Component priceLine = Component.literal(HACKSILVER_GLYPH + " " + state.price).withStyle(ChatFormatting.WHITE);

		poseStack.pushPose();
		collector.order(0).submitNameTag(poseStack, state.nameTagAttachment, 0, levelLine, !state.isDiscrete, state.lightCoords, camera);
		poseStack.translate(0, 0.25875f, 0);
		collector.order(0).submitNameTag(poseStack, state.nameTagAttachment, 0, priceLine, !state.isDiscrete, state.lightCoords, camera);
		poseStack.popPose();
	}

	@Override
	public Identifier getTextureLocation(VikingRenderState state) {
		return SKINS[state.skin];
	}
}
