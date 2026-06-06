package io.wifi.starrailexpress.api.replay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReplayDisplayUtils {

    public static MutableComponent getPlayerNames(GameReplayManager replayManager, Iterable<UUID> playerUUIDs) {
        MutableComponent names = Component.empty().copy();
        boolean first = true;

        for (UUID uuid : playerUUIDs) {
            if (!first) {
                names = names.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            names = names.append(replayManager.getPlayerName(uuid));
            first = false;
        }

        return names;
    }

    public static String getRolePath(String roleId) {
        if (roleId == null)
            return null;
        ResourceLocation id = ResourceLocation.tryParse(roleId);
        if (id == null) {
            return (roleId);
        }
        return id.getPath();
    }

    public static MutableComponent getRoleDisplayName(String roleId) {
        if (roleId == null)
            return Component.literal("");
        ResourceLocation id = ResourceLocation.tryParse(roleId);
        if (id == null) {
            // 尝试作为自定义职业路径查找（纯路径不含冒号的情况）
            if (!roleId.contains(":")) {
                var customData = io.wifi.starrailexpress.customrole.CustomRoleLoader.getCustomRoleData(roleId);
                if (customData != null && !customData.displayName.isEmpty()) {
                    return Component.literal(customData.displayName);
                }
            }
            return Component.literal(roleId);
        }
        // 如果解析出的命名空间不是 customrole，但可能是剥离了命名空间的自定义职业路径
        if (!"customrole".equals(id.getNamespace())) {
            var customData = io.wifi.starrailexpress.customrole.CustomRoleLoader.getCustomRoleData(id.getPath());
            if (customData != null && !customData.displayName.isEmpty()) {
                return Component.literal(customData.displayName);
            }
        }
        var roleName = RoleUtils.getRoleName(id);
        if (roleName != null) return roleName;
        return Component.translatable("announcement.star.role." + id.getPath());
    }

    public static MutableComponent buildTeamPlayerRoles(GameReplayManager replayManager, List<UUID> teamPlayers,
            Map<UUID, String> playerRoles, String prefix) {
        if (teamPlayers.isEmpty()) {
            return null;
        }
        MutableComponent text = Component.empty().copy();
        text.append(Component.literal(prefix).withStyle(ChatFormatting.WHITE));
        boolean first = true;
        for (UUID uuid : teamPlayers) {
            if (!first) {
                text.append(Component.literal("、").withStyle(ChatFormatting.GRAY));
            }
            Component playerName = replayManager.getPlayerName(uuid);
            String roleId = playerRoles.get(uuid);
            Component roleName = roleId != null ? getRoleDisplayName(roleId) : Component.literal("未知职业");
            text.append(playerName).append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(roleName).append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            first = false;
        }
        return text;
    }

    // 添加一个新的方法来处理带死亡状态的显示
    public static MutableComponent buildTeamPlayerRolesWithDeathStatus(GameReplayManager replayManager,
            List<UUID> teamPlayers, Map<UUID, String> playerRoles, String prefix, GameReplayData replayData,
            boolean isAlive) {
        if (teamPlayers.isEmpty()) {
            return null;
        }
        MutableComponent text = Component.empty().copy();
        text.append(Component.literal(prefix).withStyle(ChatFormatting.WHITE));
        boolean first = true;
        for (UUID uuid : teamPlayers) {
            if (!first) {
                text.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            // 获取玩家名称和角色
            MutableComponent playerName = GameReplayUtils
                    .getReplayPlayerDisplayText(uuid, replayManager, replayData, false).copy();

            // 添加死亡标记
            if (!isAlive) {
                playerName.append(
                        Component.translatable("message.replay_manager.dead").withStyle(ChatFormatting.DARK_RED));
            }
            text.append(playerName);
            first = false;
        }
        return text;
    }

    public static long findGameStartTime(GameReplayData replayData) {
        for (GameReplayData.ReplayEvent event : replayData.getTimeline()) {
            if (event.getType() == GameReplayData.EventType.GAME_START) {
                return event.getTimestamp();
            }
        }
        if (!replayData.getTimeline().isEmpty()) {
            return replayData.getTimeline().getFirst().getTimestamp();
        }
        return 0;
    }

    public static String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}