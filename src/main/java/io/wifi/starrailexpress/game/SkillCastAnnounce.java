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

package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.network.SkillCastAnnouncePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 技能释放通告：玩家成功释放技能后，按服务端配置广播给同世界玩家。
 * 白名单非空时只显示名单内职业；黑名单始终排除；名单都空时仅显示平民阵营。
 */
public final class SkillCastAnnounce {
    private SkillCastAnnounce() {
    }

    public static void tryAnnounce(ServerPlayer player, SRERole role, @Nullable RoleSkill.Definition definition) {
        if (player == null || role == null) {
            return;
        }
        SREConfig config = SREConfig.instance();
        if (config == null || !config.skillCastAnnounceHud) {
            return;
        }
        if (definition != null) {
            if (!definition.showOnHud() || definition.modeSwitch()) {
                return;
            }
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return;
        }
        if (!shouldShowRole(role, config)) {
            return;
        }
        SkillCastAnnouncePayload payload = new SkillCastAnnouncePayload(
                player.getName().getString(), role.identifier());
        for (ServerPlayer viewer : player.serverLevel().players()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    /**
     * 按指定职业通告（模仿者释放复制技能时传入被模仿的平民职业）。
     * 不要求该职业自身把技能标成 showOnHud。
     */
    public static void tryAnnounceAs(ServerPlayer player, ResourceLocation displayRoleId) {
        if (player == null || displayRoleId == null) {
            return;
        }
        SRERole displayRole = io.wifi.starrailexpress.api.TMMRoles.getRole(displayRoleId);
        if (displayRole == null) {
            return;
        }
        tryAnnounce(player, displayRole, null);
    }

    private static boolean shouldShowRole(SRERole role, SREConfig config) {
        if (matchesRoleList(role, config.skillCastAnnounceBlacklist)) {
            return false;
        }
        if (hasEntries(config.skillCastAnnounceWhitelist)) {
            return matchesRoleList(role, config.skillCastAnnounceWhitelist);
        }
        return isCivilianFaction(role);
    }

    /** 平民阵营：乘客侧（含警长），不含杀手与中立。 */
    private static boolean isCivilianFaction(SRERole role) {
        return role.isInnocent() && !role.isNeutrals() && !role.isKiller();
    }

    private static boolean hasEntries(List<String> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (String entry : list) {
            if (entry != null && !entry.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRoleList(SRERole role, List<String> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        String id = role.identifier().toString();
        String path = role.identifier().getPath();
        for (String raw : list) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim();
            if (token.equalsIgnoreCase(id) || token.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }
}
