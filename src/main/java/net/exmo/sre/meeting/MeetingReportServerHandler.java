package net.exmo.sre.meeting;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.MeetingEndEvent;
import io.wifi.starrailexpress.event.OnGameEnd;
import net.exmo.sre.meeting.network.MeetingCooldownS2CPayload;
import net.exmo.sre.meeting.network.MeetingReportC2SPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 「瞄准尸体按键召开会议」的服务端接线：
 * <ul>
 * <li>接收 {@link MeetingReportC2SPayload}，校验实体与距离后走
 * {@link MeetingManager#tryReportBody}（与右键尸体等价）；</li>
 * <li>散会 / 游戏结束 / 玩家中途加入时下发 {@link MeetingCooldownS2CPayload}，
 * 供客户端 HUD 显示剩余冷却与已上报尸体。</li>
 * </ul>
 */
public final class MeetingReportServerHandler {

    private MeetingReportServerHandler() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MeetingReportC2SPayload.ID,
                (payload, context) -> context.server().execute(
                        () -> handleReport(context.player(), payload.bodyEntityId())));

        // 散会时 MeetingManager 已把冷却算好，直接广播触发时间（见 ai_doc：同步触发时刻而非每秒同步）
        MeetingEndEvent.EVENT.register(serverLevel -> broadcast(serverLevel,
                MeetingManager.getCooldownUntilTick(), MeetingManager.getReportedBodies()));
        // 游戏结束时 MeetingManager 会清零冷却，这里显式广播清零（不依赖监听器注册顺序）
        OnGameEnd.EVENT.register((serverLevel, game) -> broadcast(serverLevel, 0, List.of()));
        // 中途加入 / 重连的玩家补发当前冷却
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(
                new MeetingCooldownS2CPayload(MeetingManager.getCooldownUntilTick(),
                        List.copyOf(MeetingManager.getReportedBodies()))));
    }

    private static void handleReport(ServerPlayer player, int bodyEntityId) {
        if (!(player.serverLevel().getEntity(bodyEntityId) instanceof PlayerBodyEntity body)) {
            return;
        }
        if (!player.canInteractWithEntity(body, 3.0)) {
            return;
        }
        // 启用开关 / 存活 / 重复上报 / 冷却均由 tryReportBody 校验
        MeetingManager.tryReportBody(player, body);
    }

    private static void broadcast(ServerLevel serverLevel, long cooldownEndGameTime, Collection<UUID> reported) {
        MeetingCooldownS2CPayload payload = new MeetingCooldownS2CPayload(
                cooldownEndGameTime, List.copyOf(reported));
        for (ServerPlayer player : serverLevel.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
