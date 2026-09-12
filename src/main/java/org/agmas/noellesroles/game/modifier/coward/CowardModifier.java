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

package org.agmas.noellesroles.game.modifier.coward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 胆小鬼修饰符：持续挂上胆小鬼药水。
 */
public final class CowardModifier {
    private static final int EFFECT_DURATION = 200;
    private static final int REFRESH_THRESHOLD = 40;

    private CowardModifier() {
    }

    public static void serverTick(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.COWARD);
        if (current != null
                && (current.isInfiniteDuration() || current.getDuration() > REFRESH_THRESHOLD)) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.COWARD, EFFECT_DURATION, 0, false, false, true));
    }
}
