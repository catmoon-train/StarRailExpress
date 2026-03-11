package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenSkinScreenPaylod() implements CustomPacketPayload {
	public static final Type<OpenSkinScreenPaylod> ID = new Type<>(SRE.id("open_skin_screen"));
	public static final StreamCodec<FriendlyByteBuf, OpenSkinScreenPaylod> CODEC = CustomPacketPayload.codec(OpenSkinScreenPaylod::encode, OpenSkinScreenPaylod::decode);

	public static final OpenSkinScreenPaylod INSTANCE = new OpenSkinScreenPaylod();

	public static void encode(OpenSkinScreenPaylod payload, FriendlyByteBuf buf) {
	}

	public static OpenSkinScreenPaylod decode(FriendlyByteBuf buf) {
		return INSTANCE;
	}

	@Override
	public Type<OpenSkinScreenPaylod> type() {
		return ID;
	}
}