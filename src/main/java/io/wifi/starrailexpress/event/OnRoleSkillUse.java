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

package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;

public class OnRoleSkillUse {
    public static Event<UseSkillEventInterface> BEFORE = EventFactory.createArrayBacked(UseSkillEventInterface.class,
            listeners -> (player, deathReason) -> {
                for (UseSkillEventInterface listener : listeners) {
                    if (!listener.onUse(player, deathReason))
                        return false;
                }
                return true;
            });
    public static Event<UseSkillEventInterface> AFTER = EventFactory.createArrayBacked(UseSkillEventInterface.class,
            listeners -> (player, deathReason) -> {
                for (UseSkillEventInterface listener : listeners) {
                    if (!listener.onUse(player, deathReason))
                        return false;
                }
                return true;
            });

    public interface UseSkillEventInterface {
        boolean onUse(Player player, SRERole role);
    }
}
