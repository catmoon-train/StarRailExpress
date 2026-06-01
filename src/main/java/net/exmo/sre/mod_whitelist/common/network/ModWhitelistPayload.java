package net.exmo.sre.mod_whitelist.common.network;

import io.wifi.starrailexpress.SRE;
import net.exmo.sre.mod_whitelist.common.ModInfo;
import net.exmo.sre.mod_whitelist.common.ResourcePackInfo;
import net.exmo.sre.mod_whitelist.common.ShaderPackInfo;
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
public record ModWhitelistPayload(
    List<ModInfo> mods, 
    boolean includeHashes,
    List<ResourcePackInfo> resourcePacks,
    List<ShaderPackInfo> shaderPacks
) implements CustomPacketPayload {
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
			ByteBufCodecs.BOOL,
			ModWhitelistPayload::includeHashes,
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::sha256,
							ResourcePackInfo::new
					)
			),
			ModWhitelistPayload::resourcePacks,
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ShaderPackInfo::sha256,
							ShaderPackInfo::new
					)
			),
			ModWhitelistPayload::shaderPacks,
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
		if (resourcePacks == null) {
			throw new IllegalArgumentException("resourcePacks cannot be null");
		}
		if (shaderPacks == null) {
			throw new IllegalArgumentException("shaderPacks cannot be null");
		}
	}
}