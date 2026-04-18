package org.agmas.noellesroles.game.roles.killer.creeper;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;

import java.util.List;

/**
 * 彩虹苦力怕角色类
 */
public class RainbowCreeperRole extends NormalRole {

    public RainbowCreeperRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public int getColor() {
        return color();
    }

    @Override
    public int color() {
        long lastVisitColorTime = System.currentTimeMillis();
        long cycleMs = 5000; // 5秒一个完整彩虹
        float hue = (lastVisitColorTime % cycleMs) / (float) cycleMs;
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(
                new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 130, ShopEntry.Type.WEAPON),
                new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 130, ShopEntry.Type.TOOL));
    }

    @Override
    public void onAbilityUse(Player player) {
        CreeperPlayerComponent component = ModComponents.CREEPER.get(player);
        component.ignite();
    }
}