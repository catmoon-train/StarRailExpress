package io.wifi.ConfigCompact.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端：有权限的玩家在配置 GUI 中修改了 {@code @ConfigSync} 字段后，
 * 把这些字段上传给服务端，由服务端校验权限、写盘并广播给所有客户端。
 * 解决“服务端用 GUI 改的服务端权威配置不落盘、重启即重置”的问题。
 */
public record UploadConfigPayload(String configId, String content) implements CustomPacketPayload {
    public static final Type<UploadConfigPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "upload_config"));
    public static final StreamCodec<FriendlyByteBuf, UploadConfigPayload> CODEC = StreamCodec
            .ofMember(UploadConfigPayload::encode, UploadConfigPayload::decode);

    public static UploadConfigPayload decode(FriendlyByteBuf buf) {
        return new UploadConfigPayload(buf.readUtf(), buf.readUtf());
    }

    public static void encode(UploadConfigPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(payload.configId);
        buf.writeUtf(payload.content);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
