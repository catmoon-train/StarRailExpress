/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.UUID;

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
        var id = ResourceLocation.tryParse(path);
        if (id != null) {
            var name = RoleUtils.getRoleName(id);
            if (name != null) return name.copy().withColor(getRoleColor(path));
        }
        return Component.translatable("announcement.star.role." + path).withColor(getRoleColor(path));
    }

    public static Component getRoleNameWithSourceTMMColor(String path) {
        var id = ResourceLocation.tryParse(path);
        if (id != null) {
            var name = RoleUtils.getRoleName(id);
            if (name != null) return name.copy().withStyle(getTMMRoleColor(path));
        }
        return Component.translatable("announcement.star.role." + path).withStyle(getTMMRoleColor(path));
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
        return getReplayPlayerDisplayText(playerUid, manager, replayData, notNull, Integer.MAX_VALUE, false);
    }

    public static Component getReplayPlayerDisplayText(UUID playerUid, GameReplayManager manager,
            GameReplayData replayData, boolean notNull, int untilTimelineIndexExclusive) {
        return getReplayPlayerDisplayText(playerUid, manager, replayData, notNull, untilTimelineIndexExclusive, false);
    }

    /**
     * @param untilTimelineIndexExclusive 只应用该下标之前的换职记录；{@link Integer#MAX_VALUE} 表示截至当前时间线末尾
     * @param showInitialIfChanged       终局名单等场景：若职业变过，显示为 现职业(初始职业)
     */
    public static Component getReplayPlayerDisplayText(UUID playerUid, GameReplayManager manager,
            GameReplayData replayData, boolean notNull, int untilTimelineIndexExclusive,
            boolean showInitialIfChanged) {
        if (playerUid == null && !notNull)
            return null;
        Component sourceName = playerUid != null ? manager.getPlayerName(playerUid)
                : Component.translatable("sre.replay.event.unknown_player").withStyle(ChatFormatting.ITALIC)
                        .withStyle(ChatFormatting.GRAY);
        if (playerUid == null) {
            return sourceName;
        }

        String roleAtTime = replayData == null ? null
                : replayData.resolvePlayerRoleId(playerUid, untilTimelineIndexExclusive);
        if (roleAtTime == null) {
            roleAtTime = getLiveRoleId(playerUid);
        }
        String initialRoleId = replayData == null ? null : replayData.getInitialPlayerRoleId(playerUid);
        if (roleAtTime == null) {
            roleAtTime = initialRoleId;
        }
        return appendRole(sourceName, roleAtTime, initialRoleId, showInitialIfChanged);
    }

    private static String getLiveRoleId(UUID playerUid) {
        if (playerUid == null || SRE.SERVER == null) {
            return null;
        }
        try {
            var world = SRE.SERVER.getLevel(Level.OVERWORLD);
            if (world == null) {
                return null;
            }
            var role = SREGameWorldComponent.KEY.get(world).getRole(playerUid);
            return role == null ? null : role.identifier().toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Component appendRole(Component sourceName, String roleId, String initialRoleId,
            boolean showInitialIfChanged) {
        if (roleId == null || roleId.isBlank()) {
            if (initialRoleId == null || initialRoleId.isBlank()) {
                return sourceName;
            }
            roleId = initialRoleId;
            showInitialIfChanged = false;
        }
        MutableComponent roleName = ReplayDisplayUtils.getRoleDisplayName(roleId);
        ChatFormatting tmmColor = getTMMRoleColor(roleId);
        int roleColor = getRoleColor(roleId);
        if (showInitialIfChanged && initialRoleId != null && !initialRoleId.isBlank()
                && !initialRoleId.equals(roleId)) {
            MutableComponent initialName = ReplayDisplayUtils.getRoleDisplayName(initialRoleId);
            if (UseTMMColor) {
                return sourceName.copy().withStyle(tmmColor)
                        .append(Component.translatable(" (%s(%s))", roleName.withStyle(tmmColor),
                                initialName.withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
            }
            return sourceName.copy().withStyle(tmmColor)
                    .append(Component.translatable(" (%s(%s))", roleName.withColor(roleColor),
                            initialName.withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY));
        }
        if (UseTMMColor) {
            return sourceName.copy()
                    .append(Component.translatable(" (%s)", roleName.withStyle(tmmColor)).withStyle(ChatFormatting.GRAY))
                    .withStyle(tmmColor);
        }
        return sourceName.copy()
                .append(Component.translatable(" (%s)", roleName.withColor(roleColor)).withStyle(ChatFormatting.GRAY))
                .withStyle(tmmColor);
    }

    public static Component getItemStackDisplayNameWithCounts(ItemStack stack) {
        if(stack == null || stack.isEmpty()){
            return Items.AIR.getDefaultInstance().getDisplayName();
        }
        return stack.getDisplayName().copy().append("(").append(Integer.toString(stack.getCount())).append(")");
    }
}
