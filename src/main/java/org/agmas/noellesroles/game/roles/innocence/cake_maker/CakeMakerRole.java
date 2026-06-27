package org.agmas.noellesroles.game.roles.innocence.cake_maker;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class CakeMakerRole extends NormalRole {
    public CakeMakerRole(ResourceLocation id, int color, boolean innocent, boolean killer, MoodType mood, int sprint, boolean hide) {
        super(id, color, innocent, killer, mood, sprint, hide);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        if ("eat".equals(quest) || "drink".equals(quest)) {
            SREPlayerShopComponent.KEY.get(player).addToBalance(25);
        }
    }
}
