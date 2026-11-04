package com.valhalla.command.client.render;

import com.valhalla.command.ValhallaCommand;
import com.valhalla.command.entity.VikingEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** Draws Vikings with the normal player model and one of 20 skins. */
public class VikingRenderer extends HumanoidMobRenderer<VikingEntity, VikingRenderState, HumanoidModel<VikingRenderState>> {
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
	}

	@Override
	public Identifier getTextureLocation(VikingRenderState state) {
		return SKINS[state.skin];
	}
}
