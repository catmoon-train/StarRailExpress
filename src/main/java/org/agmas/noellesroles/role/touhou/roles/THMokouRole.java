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
import java.util.List;

import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;

public class THMokouRole extends TouhouRole {
    public static final int XIAONAO_THRESHOLD = 8;
    public static final int NORMAL_DEATH_THRESHOLD = 5;

    public THMokouRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean winWithKiller() {
        return false;
    }

    @Override
    public boolean canIncreaseSurvivingInnocents() {
        return true;
    }

    @Override
    public boolean winWithInnocent() {
        return false;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new ShopEntry(ModItems.FAKE_REVOLVER.getDefaultInstance(),
                50, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(ModItems.ONCE_REVOLVER.getDefaultInstance(),
                150, ShopEntry.Type.WEAPON));
        return SHOP;
    }

    @Override
    public boolean canBeXiaonao(Player victim, Player killer, ResourceLocation deathReason) {
        if (!(victim instanceof ServerPlayer serverVictim))
            return false;
        int remaningPlayerCount = RoleUtils.getAlivePlayers(serverVictim.serverLevel()).size();
        if (remaningPlayerCount <= XIAONAO_THRESHOLD)
            return true;
        return false;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (!(victim instanceof ServerPlayer serverVictim))
            return;
        int remaningPlayerCount = RoleUtils.getAlivePlayers(serverVictim.serverLevel()).size();
        if (remaningPlayerCount <= NORMAL_DEATH_THRESHOLD)
            return;
        if (deathReason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN))
            return;
        if (deathReason.equals(GameConstants.DeathReasons.BROKEN_HEART)
                || (killer != null && !SREGameWorldComponent.isInnocentStatic(killer) && !forceDeath)) {

            var lover = LoversComponent.KEY.get(victim).getLoverAsPlayer();
            if (lover != null) {
                if (!GameUtils.isPlayerAliveAndSurvival(lover)
                        && DefibrillatorComponent.KEY.get(lover).resurrectionTime <= 0) {
                    return;
                }
            }
            DefibrillatorComponent component = ModComponents.DEFIBRILLATOR.get(victim);
            component.triggerDeath(30 * 20, null, victim.position());
            SREPlayerShopComponent.KEY.get(victim).addToBalance(50);
        }
    }
}
