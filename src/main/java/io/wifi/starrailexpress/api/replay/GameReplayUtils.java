package io.wifi.starrailexpress.api.replay;

import java.util.UUID;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GameReplayUtils {
    public static boolean UseTMMColor = true;

    public static Component getItemDisplayName(ResourceLocation itemId) {
        Item item = GameReplayData.DEATH_REASON_TO_ITEM.get(itemId);
        if (item != null) {
            return new ItemStack(item).getDisplayName();
        }
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            return stack.getDisplayName();
        }
        // 返回本地化的死亡原因
        if (itemId.getNamespace() == null)
            return Component.translatable("death_reason.starrailexpress." + itemId.getPath());
        else
            return Component.translatable("death_reason." + itemId.getNamespace() + "." + itemId.getPath());
    }

    public static Component getRoleNameWithRoleColor(String path) {
        String translationKey = "announcement.star.role." + path;
        return Component.translatable(translationKey).withColor(getRoleColor(path));
    }

    public static Component getRoleNameWithSourceTMMColor(String path) {
        String translationKey = "announcement.star.role." + path;
        return Component.translatable(translationKey).withStyle(getTMMRoleColor(path));
    }

    public static Component getReplayPlayerDisplayText(Player player, boolean notNull) {
        if (SRE.REPLAY_MANAGER != null) {
            return getReplayPlayerDisplayText(player, SRE.REPLAY_MANAGER, SRE.REPLAY_MANAGER.currentReplayData,
                    notNull);
        }
        return player.getDisplayName();
    }

    public static Component getReplayPlayerDisplayText(Player player, GameReplayManager manager,
            GameReplayData replayData, boolean notNull) {
        if (player == null)
            return Component.translatable("sre.replay.event.unknown_player").withStyle(ChatFormatting.OBFUSCATED)
                    .withStyle(ChatFormatting.GRAY);
        return getReplayPlayerDisplayText(player.getUUID(), manager, replayData, notNull);
    }

    public static int getRoleColor(String roleId) {
        if (roleId == null) {
            return java.awt.Color.WHITE.getRGB(); // 默认颜色
        }
        final var first = TMMRoles.ROLES.values().stream().filter(
                role -> role.identifier().toString().equals(roleId) || role.identifier().getPath().equals(roleId))
                .findFirst();
        // 根据角色ID分类
        if (first.isPresent()) {
            var role = first.get();
            if (role != null) {
                return role.getColor();
            }
        }
        return java.awt.Color.WHITE.getRGB();
    }

    public static ChatFormatting getTMMRoleColor(String roleId) {
        if (roleId == null) {
            return ChatFormatting.WHITE; // 默认颜色
        }
        final var first = TMMRoles.ROLES.values().stream().filter(
                role -> role.identifier().toString().equals(roleId) || role.identifier().getPath().equals(roleId))
                .findFirst();
        // 根据角色ID分类
        if (first.isPresent()) {
            var role = first.get();
            if (role != null) {
                if (role.isVigilanteTeam()) {
                    return ChatFormatting.AQUA;
                } else if (role.isInnocent()) {
                    return ChatFormatting.GREEN;
                } else if (role.canUseKiller()) {
                    return ChatFormatting.RED;
                } else if (role.isNeutralForKiller()) {
                    return ChatFormatting.LIGHT_PURPLE;
                } else if (!role.isInnocent() || role.isNeutrals()) {
                    return ChatFormatting.YELLOW;
                }
            }
        }
        return ChatFormatting.WHITE;
    }

    public static Component getReplayPlayerDisplayText(UUID playerUid, GameReplayManager manager,
            GameReplayData replayData, boolean notNull) {
        if (playerUid == null && !notNull)
            return null;
        Component sourceName = playerUid != null ? manager.getPlayerName(playerUid)
                : Component.translatable("sre.replay.event.unknown_player").withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY);
        String sourceRoleId = playerUid != null ? replayData.getPlayerRoles().get(playerUid)
                : TMMRoles.CIVILIAN.identifier().toString();
        sourceRoleId = ReplayDisplayUtils.getRolePath(sourceRoleId);
        String sourceRoleIdNow = playerUid != null
                ? SREGameWorldComponent.KEY.get(SRE.SERVER.getLevel(Level.OVERWORLD)).getRole(playerUid) == null ? null
                        : SREGameWorldComponent.KEY.get(SRE.SERVER.getLevel(Level.OVERWORLD)).getRole(playerUid)
                                .identifier().getPath()
                : null;
        sourceRoleIdNow = ReplayDisplayUtils.getRolePath(sourceRoleIdNow);

        MutableComponent sourceRoleName = ReplayDisplayUtils.getRoleDisplayName(sourceRoleId);
        int sourceRoleColor = getRoleColor(sourceRoleId);

        ChatFormatting sourceTMMColor = getTMMRoleColor(sourceRoleId);

        // 如果当前角色与记录的角色不同，则显示为(new(old))格式，old为灰色
        if (sourceRoleId == null) {
            if (sourceRoleIdNow != null) {
                MutableComponent currentRoleName = ReplayDisplayUtils.getRoleDisplayName(sourceRoleIdNow);
                int currentColor = getRoleColor(sourceRoleIdNow);

                sourceName = sourceName.copy().withStyle(sourceTMMColor)
                        .append(Component.translatable(" (%s)", currentRoleName.withColor(currentColor),
                                sourceRoleName.withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
            } else {

            }

        } else if (sourceRoleIdNow != null && !sourceRoleId.equals(sourceRoleIdNow)) {
            MutableComponent currentRoleName = ReplayDisplayUtils.getRoleDisplayName(sourceRoleIdNow);
            int currentColor = getRoleColor(sourceRoleIdNow);
            ChatFormatting currentTMMColor = getTMMRoleColor(sourceRoleIdNow);
            if (UseTMMColor) {
                // currentTMMColor
                sourceName = sourceName.copy().withStyle(sourceTMMColor)
                        .append(Component.translatable(" (%s(%s))", currentRoleName.withStyle(currentTMMColor),
                                sourceRoleName.withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
            } else {
                sourceName = sourceName.copy().withStyle(sourceTMMColor)
                        .append(Component.translatable(" (%s(%s))", currentRoleName.withColor(currentColor),
                                sourceRoleName.withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
            }

        } else {
            // 新老职业相同，只显示当前职业，不加括号
            if (UseTMMColor) {
                sourceName = sourceName.copy()
                        .append(Component.translatable(" (%s)", sourceRoleName.withStyle(sourceTMMColor))
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(sourceTMMColor);
            } else {
                sourceName = sourceName.copy()
                        .append(Component.translatable(" (%s)", sourceRoleName.withColor(sourceRoleColor))
                                .withStyle(ChatFormatting.GRAY))
                        .withStyle(sourceTMMColor);
            }
        }
        return sourceName;
    }
}
