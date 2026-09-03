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

import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class THRemiliaServantRole extends TouhouRole {

    public THRemiliaServantRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        setCanAutoAddMoney(true);
        setCanEarnKillerCoinAwardsFromKills(true);
    }

    @Override
    public boolean canIncreaseSurvivingKillers() {
        return true;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new KillerKnifeShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice, 50));
        SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));
        SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        return SHOP;
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {
        clearTaskRewardTracking(serverPlayer.getUUID());
        MoneyUtils.setBalance(serverPlayer, 0);
        MCItemsUtils.insertStackInFreeSlot(serverPlayer, TMMItems.KNIFE.getDefaultInstance());
    }
}
