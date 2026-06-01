package net.exmo.sre.mod_whitelist.common.network;

import io.wifi.starrailexpress.SRE;
import net.exmo.sre.mod_whitelist.common.ShaderPackInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ShaderPackWhitelistPayload(
		List<ShaderPackInfo> shaderPacks,
		boolean includeHashes
) implements CustomPacketPayload {
	public static final Type<ShaderPackWhitelistPayload> ID = new Type<>(SRE.id("shader_pack_whitelist"));

	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ShaderPackWhitelistPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::sha256,
							ShaderPackInfo::new
					)
			),
			ShaderPackWhitelistPayload::shaderPacks,
			ByteBufCodecs.BOOL,
			ShaderPackWhitelistPayload::includeHashes,
			ShaderPackWhitelistPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public ShaderPackWhitelistPayload {
		if (shaderPacks == null) {
			throw new IllegalArgumentException("shaderPacks cannot be null");
		}
	}
}
