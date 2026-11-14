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

package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

/** 布袋鬼统一生命周期与护盾后替死；不按玩家动态注册事件。 */
public final class MaChenXuEventHandler {
    private static final ResourceLocation ESCAPE_PHASE = Noellesroles.id("sack_ghost_escape_after_shields");
    private static boolean registered;
    private MaChenXuEventHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        SackGhostActions.register();
        SackGhostItems.register();
        // 临时无敌在普通护盾之前拦截，避免无敌期间平白消耗护盾；本体致死检查会显式放行。
        AllowPlayerDeath.EVENT.register((victim, reason) -> {
            var data = SackGhostItems.data(victim);
            return data == null || GameUtils.isForcedDeathInProgress() || !data.protectsFromDeath();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            if (!(victim instanceof Player player)) return true;
            var data = SackGhostItems.data(player);
            return data == null || !data.protectsFromDeath();
        });
        // 普通、角色及既有克隆替身均先结算，再消费布袋鬼本人的替死次数。
        AfterShieldAllowPlayerDeathWithKiller.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, ESCAPE_PHASE);
        AfterShieldAllowPlayerDeathWithKiller.EVENT.addPhaseOrdering(Noellesroles.id("clone_substitute_after_shields"), ESCAPE_PHASE);
        AfterShieldAllowPlayerDeathWithKiller.EVENT.register(ESCAPE_PHASE, (victim, killer, reason) -> {
            var game = SREGameWorldComponent.KEY.get(victim.level());
            if (!game.isRole(victim, ModRoles.MA_CHEN_XU)) return true;
            var data = SackGhostItems.data(victim);
            if (data != null && !data.afterShieldsAllowDeath()) return false;
            if (game.isSkillAvailable) RoleUtils.changeRole(victim, TMMRoles.DISCOVERY_CIVILIAN);
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var data = SackGhostItems.data(handler.player);
            if (data != null) data.clear();
            for (var owner : handler.player.serverLevel().players()) {
                var other = SackGhostItems.data(owner);
                if (other != null) other.cleanse(handler.player);
            }
            handler.player.removeEffect(org.agmas.noellesroles.init.ModEffects.GHOST_CONDEMNED);
            SackGhostItems.clearWard(handler.player);
        });
    }
}
