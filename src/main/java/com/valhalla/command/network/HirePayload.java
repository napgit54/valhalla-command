package com.valhalla.command.network;

import com.valhalla.command.ValhallaCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent from the client when the player picks "Hire" on a Viking's wheel. */
public record HirePayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HirePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "hire"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HirePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HirePayload::entityId,
			HirePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
