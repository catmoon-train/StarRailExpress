package io.wifi.ConfigCompact.network;

import com.google.gson.Gson;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI.RoleAndModifierSyncInfo;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RoleEnableInfoPacket(RoleAndModifierSyncInfo packetInfo) implements CustomPacketPayload {
    public static final Gson gson = new Gson();
    public static final Type<RoleEnableInfoPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "roles_config"));
    public static final StreamCodec<FriendlyByteBuf, RoleEnableInfoPacket> CODEC = StreamCodec
            .ofMember(RoleEnableInfoPacket::encode, RoleEnableInfoPacket::decode);

    public static RoleEnableInfoPacket decode(FriendlyByteBuf buf) {
        String str = buf.readUtf();
        try {
            return new RoleEnableInfoPacket(gson.fromJson(str, RoleAndModifierSyncInfo.class));
        } catch (Exception e) {
            SRE.LOGGER.error("ERROR WHILE RECIEVING PAYLOAD FROM SERVER.", e);
        }
        return new RoleEnableInfoPacket(new RoleAndModifierSyncInfo());
    }

    public static void encode(RoleEnableInfoPacket payload, FriendlyByteBuf buf) {
        buf.writeUtf(gson.toJson(payload.packetInfo()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}