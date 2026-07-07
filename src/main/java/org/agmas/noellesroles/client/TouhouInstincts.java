package org.agmas.noellesroles.client;

import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.BatItem;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.event.client.OnGetInstinctHighlight;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class TouhouInstincts {

    public static void registerEvents() {

        // 四季
        OnGetInstinctHighlight.ALIVE_EVENT.register((target, hasInstinct) -> {
            if (!hasInstinct || Minecraft.getInstance().player == null || SREClient.gameComponent == null) {
                return -1;
            }
            Player self = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(self, THMiscRoles.SHIKIEIKI)) {
                return -1;
            }
            if (target instanceof Player targetPlayer) {
                var mainhandItem = targetPlayer.getMainHandItem();
                if (targetPlayer.distanceToSqr(self) <= 5 * 5) {
                    if (mainhandItem.getItem() instanceof BatItem || mainhandItem.getItem() instanceof KnifeItem
                            || mainhandItem.is(TMMItemTags.GUNS)) {
                        return java.awt.Color.ORANGE.getRGB();
                    }
                }
                var cca = SREAbilityPlayerComponent.KEY.get(self);
                if (cca.duration <= 0 || cca.targetUUID == null) {
                    return -1;
                }
                if (targetPlayer.getUUID().equals(cca.targetUUID)) {
                    return java.awt.Color.CYAN.getRGB();
                }
            }
            return -1;
        });
    }

    public static boolean isKillerTeam(SRERole role) {
        if (SREClient.gameComponent == null) {
            return false;
        }
        return SREClient.gameComponent.isKillerTeamRole(role);
    }
}
