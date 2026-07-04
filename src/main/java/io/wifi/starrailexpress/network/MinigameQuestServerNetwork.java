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
     * 若为破坏任务触发点，则启动破坏任务而非发放代币。
     */
    private static void handleCompleteGame(MinigameQuestPayload.CompleteGame payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.getServer().execute(() -> {
            BlockPos pos = payload.pos();
            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                // 破坏任务触发点：启动破坏任务（含冷却检查）
                if (questBe.isSabotageTrigger() && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getRole(player);
                    if (role == null || (!role.isKiller() && !role.canUseSabotage())) {
                        return;
                    }
                    long now = level.getGameTime();
                    long cooldownTicks = (long) questBe.getSabotageCooldown() * 20;
                    if (cooldownTicks > 0 && questBe.getLastSabotageTime() > 0
                            && now - questBe.getLastSabotageTime() < cooldownTicks) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.sre.sabotage_cooldown"),
                                true);
                        return;
                    }
                    questBe.setLastSabotageTime(now);
                    org.agmas.noellesroles.scene.SceneEventManager.startSabotage(level,
                            questBe.getSabotageDuration() * 20);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    return;
                }

                // 统一完成标识：blockEvent(type=1, data=0)
                player.level().blockEvent(pos, questBe.getBlockState().getBlock(), 1, 0);
                questBe.setChanged();
                // 完成反馈：在方块处显示庆祝粒子 + 音效
                if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    double cx = pos.getX() + 0.5;
                    double cy = pos.getY() + 0.5;
                    double cz = pos.getZ() + 0.5;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            cx, cy, cz, 24, 0.4, 0.5, 0.4, 0.0);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                            cx, cy + 0.3, cz, 18, 0.4, 0.5, 0.4, 0.15);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            cx, cy + 0.5, cz, 10, 0.3, 0.4, 0.3, 0.05);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
                }
                // 小游戏任务系统：若该方块正是玩家被指派的目标，则发放游戏代币
                if (io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(player.level()).minigameQuestEnabled) {
                    io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player)
                            .onMinigameBlockCompleted(player, pos,
                                    io.wifi.starrailexpress.game.GameConstants.MINIGAME_TASK_TOKEN_REWARD,
                                    questBe.getMinigameId());
                }
            }
        });
    }

    /** 发送打开配置界面（服务端→客户端，只发纯数据） */
    public static void sendOpenConfig(ServerPlayer player, BlockPos pos, MinigameQuestBlockEntity entity) {
        CompoundTag data = new CompoundTag();
        data.putString("MinigameId", entity.getMinigameId());
        data.putInt("MarkerColor", entity.getMarkerColor());
        data.putBoolean("IsTaskMarker", entity.isTaskMarker());
        data.putBoolean("IsSabotageTrigger", entity.isSabotageTrigger());
        data.putInt("SabotageDuration", entity.getSabotageDuration());
        data.putInt("SabotageCooldown", entity.getSabotageCooldown());
        ServerPlayNetworking.send(player, new MinigameQuestPayload.OpenConfig(pos, data));
    }
}
