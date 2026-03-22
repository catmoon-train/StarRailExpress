package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

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
            return Component.literal(roleId);
        }
        String translationKey = "announcement.star.role." + id.getPath();
        var translated = Component.translatable(translationKey);
        return translated;
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
            List<UUID> teamPlayers, Map<UUID, String> playerRoles, String prefix, boolean isAlive) {
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
            Component playerName = replayManager.getPlayerName(uuid);
            String roleId = playerRoles.get(uuid);
            Component roleName = roleId != null ? getRoleDisplayName(roleId) : Component.literal("未知职业");

            // 根据角色设置颜色
            ChatFormatting roleColor = getRoleColor(roleId);

            // 添加玩家名和角色，并标记死亡状态
            MutableComponent playerComponent = Component.empty();
            playerComponent.append(playerName.copy().withStyle(roleColor));

            // 添加死亡标记
            if (!isAlive) {
                playerComponent.append(Component.translatable("message.replay_manager.dead").withStyle(ChatFormatting.DARK_RED));
            }

            playerComponent.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(roleName).append(Component.literal(")").withStyle(ChatFormatting.GRAY));

            text.append(playerComponent);
            first = false;
        }
        return text;
    }

    private static ChatFormatting getRoleColor(String roleId) {
        if (roleId == null) {
            return ChatFormatting.WHITE; // 默认颜色
        }
        final var first = TMMRoles.ROLES.values().stream().filter(role -> role.identifier().toString().equals(roleId))
                .findFirst();
        if (first.isPresent()) {
            final var role = first.get();
            if (role.isInnocent()) {
                return ChatFormatting.GREEN;
            }
            if (role.canUseKiller()) {
                return ChatFormatting.RED;
            }
            if (!role.isInnocent()) {
                return ChatFormatting.YELLOW;
            }
        }
        // 根据角色类型返回对应颜色
        if (roleId.equals(TMMRoles.CIVILIAN.identifier().toString()) ||
                roleId.equals(TMMRoles.DISCOVERY_CIVILIAN.identifier().toString())) {
            return ChatFormatting.BLUE; // 民兵蓝色
        } else if (roleId.equals(TMMRoles.KILLER.identifier().toString())) {
            return ChatFormatting.DARK_RED; // 杀手深红色
        } else if (roleId.equals(TMMRoles.VIGILANTE.identifier().toString())) {
            return ChatFormatting.GOLD; // 侦探金色
        } else if (roleId.equals(TMMRoles.LOOSE_END.identifier().toString())) {
            return ChatFormatting.YELLOW; // 中立黄色
        } else {
            return ChatFormatting.GRAY; // 其他角色灰色
        }
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