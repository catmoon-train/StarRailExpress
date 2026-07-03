package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class SeekerRole extends NormalRole {

    public SeekerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("other_gamemode");
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice,
                ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(), SREConfig.instance().revolverPrice,
                ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.instance().grenadePrice,
                ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice,
                ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice,
                ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice * 2,
                ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                if (SREPlayerShopComponent.useBlackout(player, 200)) {
                    for (Player p : player.level().players()) {
                        if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                            p.addEffect(new MobEffectInstance(
                                    MobEffects.GLOWING,
                                    (int) (3 * 20), // 持续时间（tick）
                                    0, // 等级（0 = 速度 I）
                                    false, // ambient（环境效果，如信标）
                                    true, // showParticles（显示粒子）
                                    true // showIcon（显示图标）
                            ));
                        }
                    }
                    return true;
                }
                return false;
            }
        });
        return shop;
    }
}
