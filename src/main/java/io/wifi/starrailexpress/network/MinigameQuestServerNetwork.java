package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 小游戏任务点方块 — 服务端网络处理
 */
public class MinigameQuestServerNetwork {

    public static void register() {
        // 客户端保存配置
        ServerPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.SaveConfig.TYPE,
                MinigameQuestServerNetwork::handleSaveConfig);
        // 客户端通知小游戏完成
        ServerPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.CompleteGame.TYPE,
                MinigameQuestServerNetwork::handleCompleteGame);
    }

    private static void handleSaveConfig(MinigameQuestPayload.SaveConfig payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            BlockEntity be = player.level().getBlockEntity(payload.pos());
            if (be instanceof MinigameQuestBlockEntity questBe) {
                questBe.loadConfigFromTag(payload.data());
                questBe.sync();
            }
        });
    }

    /**
     * 小游戏完成 — 统一触发标识
     * 通过 blockEvent 发送信号，其他系统（任务检测、红石逻辑）可监听此事件
     */
    private static void handleCompleteGame(MinigameQuestPayload.CompleteGame payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            BlockPos pos = payload.pos();
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                // 统一完成标识：blockEvent(type=1, data=0)
                // 后续任务检测系统可监听此事件判断任务点是否完成
                player.level().blockEvent(pos, questBe.getBlockState().getBlock(), 1, 0);
                questBe.setChanged();
            }
        });
    }

    /** 发送打开配置界面（服务端→客户端，只发纯数据） */
    public static void sendOpenConfig(ServerPlayer player, BlockPos pos, MinigameQuestBlockEntity entity) {
        CompoundTag data = new CompoundTag();
        data.putString("MinigameId", entity.getMinigameId());
        data.putInt("MarkerColor", entity.getMarkerColor());
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        ServerPlayNetworking.send(player, new MinigameQuestPayload.OpenConfig(pos, data));
    }
}
