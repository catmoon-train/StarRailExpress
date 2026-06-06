package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * 支持自定义商店的自定义职业基类
 */
public class CustomNormalRole extends ExtraEffectRole {
    private List<ShopEntry> customShop;

    public CustomNormalRole(ResourceLocation id, int color, boolean isInnocent, boolean canUseKiller,
                            SRERole.MoodType mood, int maxSprintTime, boolean canSeeTime,
                            ArrayList<MobEffectInstance> effects, List<ShopEntry> shop) {
        super(id, color, isInnocent, canUseKiller, mood, maxSprintTime, canSeeTime, effects);
        this.customShop = shop;
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        if (customShop != null && !customShop.isEmpty()) return customShop;
        return super.getShopEntries();
    }

    public void setCustomShop(List<ShopEntry> shop) { this.customShop = shop; }
}
