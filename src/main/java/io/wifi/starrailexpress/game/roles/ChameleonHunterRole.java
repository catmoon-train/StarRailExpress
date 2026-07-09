package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 猎人：击杀者阵营。和变色龙一样穿全套变色龙衣服（因此同样能涂装、方块伪装），
 * 额外开局携带变色龙自带的霰弹枪——弹药与射击冷却仍由变色龙的 {@code Room} 管理。
 */
public class ChameleonHunterRole extends ChameleonRole {

    public ChameleonHunterRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return new ArrayList<>(ChameleonCompat.hunterItems());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice,
                ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice,
                ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice,
                ShopEntry.Type.TOOL));
        return shop;
    }
}
