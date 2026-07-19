package org.agmas.noellesroles.client;

import org.agmas.noellesroles.role.touhou.THMiscRoles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.event.client.OnGetInstinctHighlight;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

public class TouhouInstincts {

    public static void registerEvents() {

        // 四季
        OnGetInstinctHighlight.ALIVE_EVENT.register((self, target, hasInstinct) -> {
            if (!SREClient.gameComponent.isRole(self, THMiscRoles.SHIKIEIKI)) {
                return TrueFalseAndCustomResult.pass();
            }
            if (target instanceof Player targetPlayer) {
                var mainhandItem = targetPlayer.getMainHandItem();
                if (targetPlayer.distanceToSqr(self) <= 5 * 5) {
                    if (mainhandItem.getItem() instanceof TrainWeapon
                            || mainhandItem.is(TMMItemTags.GUNS)) {
                        return TrueFalseAndCustomResult.custom(Color.ORANGE.getRGB());
                    }
                }
                var cca = SREAbilityPlayerComponent.KEY.get(self);
                if (cca.duration <= 0 || cca.targetUUID == null) {
                    return TrueFalseAndCustomResult.pass();
                }
                if (targetPlayer.getUUID().equals(cca.targetUUID)) {
                    return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                }
            }
            return TrueFalseAndCustomResult.pass();
        });
    }

    public static boolean isKillerTeam(SRERole role) {
        if (SREClient.gameComponent == null) {
            return false;
        }
        return SREClient.gameComponent.isKillerTeamRole(role);
    }
}
