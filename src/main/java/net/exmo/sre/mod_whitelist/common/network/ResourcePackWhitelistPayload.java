package net.exmo.sre.mod_whitelist.common.network;

import io.wifi.starrailexpress.SRE;
import net.exmo.sre.mod_whitelist.common.ResourcePackInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ResourcePackWhitelistPayload(
		List<ResourcePackInfo> resourcePacks,
		boolean includeHashes
) implements CustomPacketPayload {
	public static final Type<ResourcePackWhitelistPayload> ID = new Type<>(SRE.id("resource_pack_whitelist"));

	@SuppressWarnings("UnstableApiUsage")
	public static final StreamCodec<FriendlyByteBuf, ResourcePackWhitelistPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.collection(ArrayList::new,
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::packId,
							ByteBufCodecs.STRING_UTF8,
							ResourcePackInfo::sha256,
							ResourcePackInfo::new
					)
			),
			ResourcePackWhitelistPayload::resourcePacks,
			ByteBufCodecs.BOOL,
			ResourcePackWhitelistPayload::includeHashes,
			ResourcePackWhitelistPayload::new
	);

	@Override
	public @NotNull Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public ResourcePackWhitelistPayload {
		if (resourcePacks == null) {
			throw new IllegalArgumentException("resourcePacks cannot be null");
		}
	}
}
