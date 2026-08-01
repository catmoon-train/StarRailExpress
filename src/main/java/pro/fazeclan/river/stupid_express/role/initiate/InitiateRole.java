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

package pro.fazeclan.river.stupid_express.role.initiate;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;

public class InitiateRole extends SRERole {

    public InitiateRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.setNeutrals(true);
        this.setNeutralForInnocent(true);
        this.setKillExtraCoinAwards(100);
    }

    // @Override
    // public void onFinishQuest(Player player, String quest) {
    //     // SREPlayerShopComponent.KEY.get(player).addToBalance(50);
    // }

    // 没啥用其实
}
