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

package org.agmas.harpymodloader.modded_murder;



import io.wifi.starrailexpress.api.SRERole;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModdedWeights {

    public static Map<SRERole, HashMap<UUID, Integer>> roleRounds = new HashMap<>();
    public static Map<String,Float> getWeights(){
        return HarpyModLoaderConfig.HANDLER.instance().roleWeights;
    }
    public static float getRoleWeight(SRERole role){
        var customWeight = getWeights().get(role.identifier().toString());
        if (customWeight != null && customWeight > 0) {
            return customWeight;
        }
        // 返回默认权重
        return 1.0f;
    }
    public static void init() {}
}