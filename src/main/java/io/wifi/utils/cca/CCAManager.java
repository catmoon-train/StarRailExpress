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

package io.wifi.utils.cca;

import org.agmas.noellesroles.init.ModEffects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CCAManager {
    public static boolean shouldBlockEntityCCAServerTick(Entity entity){
        if(entity instanceof ServerPlayer sp){
            if(sp.hasEffect(ModEffects.SKILL_FREEZED) || sp.hasEffect(ModEffects.CCA_FREEZED)){
                return true;
            }
        }
        return false;
    }
}
