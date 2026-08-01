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

package org.agmas.harpymodloader;

import io.wifi.starrailexpress.game.utils.RoleInstance;

import java.util.Map;
import java.util.Random;

public class RoleWeightedUtil extends WeightedUtil<RoleInstance> {
    public RoleWeightedUtil(Map<RoleInstance, Float> weights, Random random) {
        super(weights, random);
    }

    public RoleWeightedUtil(Map<RoleInstance, Float> weights) {
        super(weights);
    }

}