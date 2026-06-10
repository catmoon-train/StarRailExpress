package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自定义职业同步 Payload（服务端 → 客户端）
 * 编码模式参考 LootPoolsInfoS2CPacket
 */
public class CustomRoleSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "custom_role_sync");
    public static final Type<CustomRoleSyncPayload> TYPE = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomRoleSyncPayload> CODEC;

    private final int hash;
    private final String jsonContent;

    public CustomRoleSyncPayload(int hash, String jsonContent) {
        this.hash = hash;
        this.jsonContent = jsonContent;
    }

    public int hash() {
        return hash;
    }

    public String jsonContent() {
        return jsonContent;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(hash);
        buf.writeUtf(jsonContent);
    }

    public static CustomRoleSyncPayload read(FriendlyByteBuf buf) {
        int hash = buf.readInt();
        String json = buf.readUtf();
        return new CustomRoleSyncPayload(hash, json);
    }

    static {
        CODEC = StreamCodec.ofMember(CustomRoleSyncPayload::write, CustomRoleSyncPayload::read);
    }
}
