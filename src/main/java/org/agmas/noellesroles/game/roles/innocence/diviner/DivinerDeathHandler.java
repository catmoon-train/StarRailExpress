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

package org.agmas.noellesroles.game.roles.innocence.diviner;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 占卜家死亡处理：使周围 5 格内（不含占卜家自身）的玩家获得 8 秒发光效果。
 */
public class DivinerDeathHandler {

    /** 发光范围（格）。 */
    private static final double GLOW_RADIUS = 5.0D;
    /** 发光持续时间（tick）：8 秒。 */
    private static final int GLOW_TICKS = 8 * 20;

    public static void registerEvents() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (!(victim instanceof ServerPlayer sp)) {
                return;
            }
            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(victim.level());
            if (!gw.isRunning() || !gw.isRole(victim, ModRoles.DIVINER)) {
                return;
            }
            double radiusSqr = GLOW_RADIUS * GLOW_RADIUS;
            for (ServerPlayer other : sp.serverLevel().players()) {
                if (other == sp) {
                    continue;
                }
                if (other.distanceToSqr(sp) <= radiusSqr) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0,
                            false, false, true));
                }
            }
        });
    }
}
