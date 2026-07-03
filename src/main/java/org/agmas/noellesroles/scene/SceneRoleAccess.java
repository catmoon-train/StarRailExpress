package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * 场景受限区域（迷雾、井盖等）的进入门禁判定。
 * 规则：中立 / 杀手阵营，或在白名单内的特定职业可进入。创造模式玩家始终放行（便于搭建测试）。
 */
public final class SceneRoleAccess {
    private SceneRoleAccess() {
    }

    public static SRERole roleOf(Player player) {
        try {
            return SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否为中立或杀手阵营。 */
    public static boolean isKillerOrNeutral(Player player) {
        SRERole role = roleOf(player);
        return role != null && (role.canUseKiller() || role.isNeutrals() || role.isNeutralForKiller());
    }

    /**
     * 是否可进入受限区域。
     *
     * @param allowedRoleIds 额外放行的特定职业 ID（可为 null）
     */
    public static boolean canEnterRestricted(Player player, Set<ResourceLocation> allowedRoleIds) {
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        if (isKillerOrNeutral(player)) {
            return true;
        }
        if (allowedRoleIds != null && !allowedRoleIds.isEmpty()) {
            SRERole role = roleOf(player);
            if (role != null) {
                return allowedRoleIds.contains(role.identifier());
            }
        }
        return false;
    }
}
