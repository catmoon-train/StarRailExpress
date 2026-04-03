package io.wifi.starrailexpress.mod_whitelist.common.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * Payload to send mod whitelist configuration from server to client
 * Sent when player joins the game to inform client about sync settings
 */
public record ModWhitelistConfigPayload(boolean syncHashValues) implements CustomPacketPayload {
	public static final Type<ModWhitelistConfigPayload> ID = new Type<>(SRE.id("mod_whitelist_config"));
	
	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ModWhitelistConfigPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL,
			ModWhitelistConfigPayload::syncHashValues,
			ModWhitelistConfigPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}