package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import io.wifi.starrailexpress.util.ShopEntry;

public class THKomachiRole extends TouhouRole {
    public THKomachiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new KillerKnifeShopEntry(SREConfig.instance().knifePrice));
        SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                900, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                    return false;
                }
                return super.canBuy(player);
            }

            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                    return false;
                }

                return SREPlayerShopComponent.usePsychoMode(player);
            }
        });
        SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                SREConfig.instance().lockpickPrice + 150, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                SREConfig.instance().bodyBagPrice + 150, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                400, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        return SHOP;
    }

}
