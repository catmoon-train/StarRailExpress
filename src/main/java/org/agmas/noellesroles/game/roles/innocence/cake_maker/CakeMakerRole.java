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

package org.agmas.noellesroles.game.roles.innocence.cake_maker;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class CakeMakerRole extends NormalRole {
    public CakeMakerRole(ResourceLocation id, int color, boolean innocent, boolean killer, MoodType mood, int sprint, boolean hide) {
        super(id, color, innocent, killer, mood, sprint, hide);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        if ("eat".equals(quest) || "drink".equals(quest)) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(25);
        }
    }
}
