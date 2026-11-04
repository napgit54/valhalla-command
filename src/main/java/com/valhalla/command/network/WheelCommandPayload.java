package com.valhalla.command.network;

import com.valhalla.command.ValhallaCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent from the client when a command is picked on the wheel. */
public record WheelCommandPayload(int command) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WheelCommandPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "wheel_command"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WheelCommandPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WheelCommandPayload::command,
			WheelCommandPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
