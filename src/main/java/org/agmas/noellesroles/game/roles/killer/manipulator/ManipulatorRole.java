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

package org.agmas.noellesroles.game.roles.killer.manipulator;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.jetbrains.annotations.Nullable;

public class ManipulatorRole extends NormalRole {
    public ManipulatorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        final var manipulatorPlayerComponent = RoleData.getNullable(ManipulatorRoleData.class, victim);
        final var target = manipulatorPlayerComponent != null ? manipulatorPlayerComponent.target : null;
        if (target != null) {
            final var playerByUUID = victim.level().getPlayerByUUID(target);
            if (playerByUUID != null) {
                final var inControlCCA = InControlCCA.KEY.get(playerByUUID);
                inControlCCA.isControlling = false;
                inControlCCA.sync();
            }

        }
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
    }
}
