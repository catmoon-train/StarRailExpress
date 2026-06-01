package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReplaySession {
    private final MinecraftServer server;
    private GameReplayData data;
    private final List<ReplayTimelineEvent> timeline = new ArrayList<>();

    public ReplaySession(MinecraftServer server, GameReplayData data) {
        this.server = server;
        this.data = data;
    }

    public void reset(GameReplayData data) {
        this.data = data;
        this.timeline.clear();
    }

    public GameReplayData data() {
        return data;
    }

    public void addTimelineEvent(ReplayTimelineEvent event) {
        timeline.add(event);
    }

    public List<ReplayTimelineEvent> timelineSnapshot() {
        return List.copyOf(timeline);
    }

    public long startTimestamp() {
        return ReplayDisplayUtils.findGameStartTime(data);
    }

    public ReplayPlayerProfile profile(@Nullable UUID uuid) {
        if (uuid == null) {
            return ReplayPlayerProfile.unknown();
        }
        String name = GameReplayManager.playerNames.get(uuid);
        if (name == null && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                name = player.getScoreboardName();
                GameReplayManager.playerNames.put(uuid, name);
            }
        }
        if (name == null) {
            name = "Unknown(" + uuid.toString().substring(0, 8) + ")";
        }

        String roleId = data.getPlayerRoles() == null ? null : data.getPlayerRoles().get(uuid);
        String rolePath = ReplayDisplayUtils.getRolePath(roleId);
        Component roleName = rolePath == null
                ? Component.translatable("announcement.star.role." + TMMRoles.CIVILIAN.identifier().getPath())
                : ReplayDisplayUtils.getRoleDisplayName(rolePath);
        return new ReplayPlayerProfile(uuid, name, roleId, roleName, isAlive(uuid));
    }

    public boolean isAlive(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        for (GameReplayData.ReplayEvent event : data.getTimeline()) {
            if (event.getType() == GameReplayData.EventType.PLAYER_KILL && uuid.equals(event.getTargetPlayer())) {
                return false;
            }
        }
        try {
            if (SRE.SERVER != null) {
                SRERole role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY
                        .get(SRE.SERVER.getLevel(Level.OVERWORLD)).getRole(uuid);
                return role != null;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    public Map<String, String> eventData(GameReplayData.ReplayEvent event) {
        Map<String, String> result = new HashMap<>();
        result.put("legacyType", event.getType().name());
        if (event.getSourcePlayer() != null) {
            result.put("sourcePlayer", event.getSourcePlayer().toString());
        }
        if (event.getTargetPlayer() != null) {
            result.put("targetPlayer", event.getTargetPlayer().toString());
        }
        if (event.getItemUsed() != null) {
            result.put("itemUsed", event.getItemUsed());
        }
        if (event.getMessage() != null) {
            result.put("message", event.getMessage());
        }
        result.put("winningTeam", String.valueOf(data.getWinningTeam()));
        result.put("winStatus", GameUtils.WinStatus.NONE.name());
        return result;
    }
}
