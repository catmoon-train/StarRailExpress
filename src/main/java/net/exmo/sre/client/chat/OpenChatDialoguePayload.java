package net.exmo.sre.client.chat;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * S2C 网络包：服务端→客户端 发送完整对话数据 + 目标实体 ID（用于 Camera 聚焦）。
 */
public record OpenChatDialoguePayload(
        String dialogueJson,
        int targetEntityId
) implements CustomPacketPayload {

    public static final Type<OpenChatDialoguePayload> ID =
            new Type<>(SRE.id("open_chat_dialogue"));

    public static final StreamCodec<FriendlyByteBuf, OpenChatDialoguePayload> CODEC =
            CustomPacketPayload.codec(OpenChatDialoguePayload::write, OpenChatDialoguePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(dialogueJson);
        buf.writeInt(targetEntityId);
    }

    private static OpenChatDialoguePayload read(FriendlyByteBuf buf) {
        return new OpenChatDialoguePayload(buf.readUtf(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * 便捷方法：向指定玩家发送对话数据包。
     *
     * @param player         目标玩家
     * @param data           对话数据
     * @param targetEntityId 摄像机聚焦的实体 ID（-1 表示不聚焦）
     */
    public static void sendToPlayer(ServerPlayer player, ChatDialogueData data, int targetEntityId) {
        String json = ChatDialogueData.GSON.toJson(data);
        ServerPlayNetworking.send(player, new OpenChatDialoguePayload(json, targetEntityId));
    }
}
