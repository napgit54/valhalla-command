package com.valhalla.command.network;

import com.valhalla.command.ValhallaCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent from the client when the player picks Hire / Kick / Aid on a Viking's wheel. */
public record VikingActionPayload(int entityId, int action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VikingActionPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "viking_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VikingActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, VikingActionPayload::entityId,
			ByteBufCodecs.VAR_INT, VikingActionPayload::action,
			VikingActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
