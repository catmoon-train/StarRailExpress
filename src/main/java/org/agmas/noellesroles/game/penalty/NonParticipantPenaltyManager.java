package org.agmas.noellesroles.game.penalty;

import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.ModComponents;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 未参与本局游戏的玩家：整局施加死亡惩罚。
 * <p>
 * 需求：玩家没有参与本局游戏（{@link ParticipationComponent#isParticipating(Player)} 为 false）时，
 * 为其实施加项目内现成的死亡惩罚（{@link DeathPenaltyComponent}），
 * 且持续整局、不因复活或其它情况解除；拥有 OP 权限（等级 2）的玩家不受影响。
 * <p>
 * 性能：
 * <ul>
 * <li>只在游戏进行中遍历一次在线玩家（每 tick 一次，玩家数量级很小）；</li>
 * <li>状态切换是幂等的：{@link DeathPenaltyComponent#setStickyPenalty(boolean)} 在状态未变化时立即返回，
 * 不会产生同步包；</li>
 * <li>提示消息只在「首次施加」或「重新参与失败」时发送一次，用 {@link #notified} 去重；</li>
 * <li>没有额外对象分配（不构造集合、不建临时 list）。</li>
 * </ul>
 */
public final class NonParticipantPenaltyManager {

    /** 已提示过的玩家，避免每 tick 重复发消息（每局开始清空） */
    private static final Set<UUID> notified = new HashSet<>();

    /** 上一 tick 游戏是否在运行，用于检测「新一局开始」 */
    private static boolean wasRunning = false;

    private NonParticipantPenaltyManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(NonParticipantPenaltyManager::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
        boolean running = gameWorld != null && gameWorld.isRunning();

        if (!running) {
            // 游戏结束：清掉本次的提示缓存，等待下一局
            if (wasRunning) {
                wasRunning = false;
                notified.clear();
            }
            return;
        }
        if (!wasRunning) {
            wasRunning = true;
            notified.clear();
        }

        ParticipationComponent participation = ParticipationComponent.KEY.get(level);
        if (participation == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DeathPenaltyComponent penalty = ModComponents.DEATH_PENALTY.get(player);
            if (penalty == null) {
                continue;
            }
            boolean shouldPunish = !participation.isParticipating(player) && !player.hasPermissions(2);
            if (shouldPunish == penalty.stickyPenalty) {
                // 状态一致：不做任何同步，也不发消息
                continue;
            }
            penalty.setStickyPenalty(shouldPunish);
            if (shouldPunish) {
                if (notified.add(player.getUUID())) {
                    Component message = Component
                            .translatable("message.noellesroles.penalty.non_participant")
                            .withStyle(ChatFormatting.RED);
                    player.displayClientMessage(message, false);
                }
            } else {
                notified.remove(player.getUUID());
            }
        }
    }
}
