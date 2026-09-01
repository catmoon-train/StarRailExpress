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

package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.Collections;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.commands.GameUtilsCommand;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class THRemiliaRole extends TouhouRole {
    public static final int COOLDOWN_TICKS = 20 * 120;

    public THRemiliaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public InteractionResult rightClickEntity(Player player, Entity target) {
        if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player))
            return InteractionResult.PASS;
        if (target instanceof PlayerBodyEntity be
                && !org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity.isDoomedSinnerBody(be)) {
            PlayerBodyEntityComponent bdrc = PlayerBodyEntityComponent.KEY.get(be);
            bdrc.playerRole = getRandomRole().identifier();
            bdrc.sync();
            be.setDeathReason(getRandomDeathReason());
        }
        return InteractionResult.PASS;
    }

    public static SRERole getRandomRole() {
        var roles = Noellesroles.getEnableAndAvailableRoles(true);
        if (roles.isEmpty())
            return TMMRoles.KILLER;
        Collections.shuffle(roles);
        return roles.getFirst();
    }

    public static String getRandomDeathReason() {
        ArrayList<ResourceLocation> list = new ArrayList<>(
                GameUtilsCommand.DeathReasonSuggestions.getAllDeathReasons());
        Collections.shuffle(list);
        return list.getFirst().toString();
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/remilia.png");
    }
}
