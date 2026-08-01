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

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SRELoverGameMode extends SREMurderGameMode {
    public SRELoverGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        boolean noLimitLover = SREConfig.instance().enableNoLimitLoversInLoverMode;// 允许N角恋
        int loverCount = Math.round((float) players.size() * SREConfig.instance().loverModeLoversPercent);
        int t = 0;
        List<ServerPlayer> unassignedPlayers = new ArrayList<>(players);
        WorldModifierComponent wmcca = WorldModifierComponent.KEY.get(serverWorld);
        for (ServerPlayer p : unassignedPlayers) {
            if (wmcca.isModifier(p, SEModifiers.LOVERS)) {
                loverCount--;
            }
        }
        if (!noLimitLover)
            loverCount /= 2;
        unassignedPlayers.removeIf((p) -> wmcca.isModifier(p, SEModifiers.LOVERS));
        Collections.shuffle(unassignedPlayers);
        for (ServerPlayer p : unassignedPlayers) {
            if (t >= loverCount)
                break;
            if (!noLimitLover) {
                if (wmcca.isModifier(p.getUUID(), SEModifiers.LOVERS))
                    continue;
            }
            wmcca.addModifier(p.getUUID(), SEModifiers.LOVERS, false);
            ModifierAssigned.EVENT.invoker().assignModifier(p, SEModifiers.LOVERS);
            t++;
        }
        wmcca.sync();
    }
}
