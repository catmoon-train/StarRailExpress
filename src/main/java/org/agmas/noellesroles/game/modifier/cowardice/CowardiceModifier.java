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

package org.agmas.noellesroles.game.modifier.cowardice;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 怯懦修饰符：持续挂上怯懦药水。
 */
public final class CowardiceModifier {
    private static final int EFFECT_DURATION = 200;
    private static final int REFRESH_THRESHOLD = 40;

    private CowardiceModifier() {
    }

    public static void serverTick(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.COWARDICE);
        if (current != null
                && (current.isInfiniteDuration() || current.getDuration() > REFRESH_THRESHOLD)) {
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.COWARDICE, EFFECT_DURATION, 0, false, false, true));
    }
}
