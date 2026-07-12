package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block.DoorPurpose;
import net.exmo.sre.sixtyseconds.network.OpenSixtySecondsDoorS2CPacket;
import net.exmo.sre.sixtyseconds.network.OpenVisitPromptS2CPacket;
import net.exmo.sre.sixtyseconds.network.OpenVisitRequestS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 拜访请求：发起方选一支<b>别队</b> + 请求类型（交易 / 进入避难所）→ 目标队<b>任一成员先响应</b>（30s 超时自动拒绝）。
 * 同意后：进入避难所 = 传送发起方到目标队避难所出生点；交易 = 打开交易 GUI 壳（P0，实物交换留 TODO）。
 */
public final class SixtySecondsVisitSystem {
    public static final int TYPE_TRADE = 0;
    public static final int TYPE_ENTER = 1;
    public static final int TIMEOUT_TICKS = 20 * 30;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private SixtySecondsVisitSystem() {
    }

    private record Pending(int targetTeamId, int type, long expireTick) {
    }

    /** 拜访门打开：给发起方发送“可拜访的别队”列表。 */
    public static void openRequestScreen(ServerPlayer visitor) {
        SixtySecondsState.Data data = SixtySecondsState.get(visitor.serverLevel());
        int myTeam = SixtySecondsStatsComponent.KEY.get(visitor).teamId;
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.teamId == myTeam) {
                continue;
            }
            ids.add(team.teamId);
            labels.add("Team " + (team.teamId + 1) + " (" + team.members.size() + ")");
        }
        int[] idArr = ids.stream().mapToInt(Integer::intValue).toArray();
        ServerPlayNetworking.send(visitor, new OpenVisitRequestS2CPacket(idArr, labels.toArray(new String[0])));
    }

    public static void request(ServerPlayer visitor, int targetTeamId, int type) {
        ServerLevel level = visitor.serverLevel();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (SixtySecondsStatsComponent.KEY.get(visitor).teamId == targetTeamId) {
            return;
        }
        SixtySecondsState.TeamData target = data.teams.get(targetTeamId);
        if (target == null) {
            return;
        }
        PENDING.put(visitor.getUUID(), new Pending(targetTeamId, type, level.getGameTime() + TIMEOUT_TICKS));
        for (UUID uuid : target.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member
                    && GameUtils.isPlayerAliveAndSurvival(member)) {
                ServerPlayNetworking.send(member,
                        new OpenVisitPromptS2CPacket(visitor.getUUID(), visitor.getGameProfile().getName(), type));
            }
        }
        visitor.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.visit_sent"), true);
    }

    public static void respond(ServerPlayer responder, UUID visitorUuid, boolean accept) {
        Pending pending = PENDING.remove(visitorUuid);
        if (pending == null) {
            return; // 已过期 / 已响应
        }
        ServerLevel level = responder.serverLevel();
        if (!(level.getPlayerByUUID(visitorUuid) instanceof ServerPlayer visitor)) {
            return;
        }
        if (!accept) {
            visitor.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.visit_rejected")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        if (pending.type == TYPE_ENTER) {
            SixtySecondsState.TeamData target = SixtySecondsState.get(level).teams.get(pending.targetTeamId);
            if (target != null && target.shelterSpawn != null) {
                visitor.teleportTo(level, target.shelterSpawn.getX() + 0.5D, target.shelterSpawn.getY(),
                        target.shelterSpawn.getZ() + 0.5D, visitor.getYRot(), visitor.getXRot());
            }
            visitor.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.visit_enter_ok")
                    .withStyle(ChatFormatting.GREEN), false);
            // 进入避难所：建立双向对话
            SixtySecondsVisitChat.startSession(visitor, responder);
        } else {
            // 交易：打开双方交易窗（主手对主手交换）
            SixtySecondsTrade.start(visitor, responder);
            visitor.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.visit_trade_ok")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        responder.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.visit_accepted_you"),
                true);
    }

    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            if (now >= entry.getValue().expireTick) {
                if (level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer visitor) {
                    visitor.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.visit_timeout")
                                    .withStyle(ChatFormatting.GRAY), false);
                }
                it.remove();
            }
        }
    }

    public static void reset() {
        PENDING.clear();
    }
}
