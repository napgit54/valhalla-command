package com.valhalla.command.network;

import com.valhalla.command.ValhallaCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Raven's Eye orders: marked entity ids, plus an optional marked spot. */
public record RavenOrderPayload(List<Integer> targets, boolean hasArea, double x, double y, double z)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RavenOrderPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ValhallaCommand.MOD_ID, "raven_order"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RavenOrderPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RavenOrderPayload::targets,
			ByteBufCodecs.BOOL, RavenOrderPayload::hasArea,
			ByteBufCodecs.DOUBLE, RavenOrderPayload::x,
			ByteBufCodecs.DOUBLE, RavenOrderPayload::y,
			ByteBufCodecs.DOUBLE, RavenOrderPayload::z,
			RavenOrderPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
