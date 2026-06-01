package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 职业轮选客户端到服务端数据包
 * 玩家选择职业时发送
 */
public class RoleRotationSelectC2SPacket implements CustomPacketPayload {

    public static final Type<RoleRotationSelectC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "role_rotation_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleRotationSelectC2SPacket> CODEC = StreamCodec.ofMember(
            RoleRotationSelectC2SPacket::write,
            RoleRotationSelectC2SPacket::new
    );

    private final int choiceIndex; // 0-2: 选择候选职业, 3: 随机

    public RoleRotationSelectC2SPacket(RegistryFriendlyByteBuf buf) {
        this.choiceIndex = buf.readInt();
    }

    public RoleRotationSelectC2SPacket(int choiceIndex) {
        this.choiceIndex = choiceIndex;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(choiceIndex);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== 服务端处理 ====================

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.server;
            if (server == null) return;

            server.execute(() -> {
                // 获取游戏模式
                var gameWorldComponent = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                if (gameWorldComponent == null) return;

                var gameMode = gameWorldComponent.getGameMode();
                if (gameMode instanceof SRERoleRotationGameMode roleRotationMode) {
                    // 处理玩家选择
                    roleRotationMode.handlePlayerRoleSelection(player, payload.choiceIndex);
                }
            });
        });
    }

    public int getChoiceIndex() {
        return choiceIndex;
    }
}
