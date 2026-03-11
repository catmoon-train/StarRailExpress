package io.wifi.starrailexpress.mod_whitelist.common.network;

import io.wifi.starrailexpress.mod_whitelist.common.ModInfo;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload to send mod information including SHA256 hashes from client to server
 * Sent when player joins the game after handshake is complete
 */
public record ModWhitelistPayload(List<ModInfo> mods) implements CustomPacketPayload {
	public static final Type<ModWhitelistPayload> ID = new Type<>(SRE.id("mod_whitelist"));
	
	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ModWhitelistPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ModInfo::modId,
							ByteBufCodecs.STRING_UTF8,
							ModInfo::sha256,
							ModInfo::new
					)
			),
			ModWhitelistPayload::mods,
			ModWhitelistPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public ModWhitelistPayload {
		if (mods == null) {
			throw new IllegalArgumentException("mods cannot be null");
		}
	}
}
