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

package org.agmas.noellesroles.client;

import org.agmas.noellesroles.role.touhou.THMagicForestRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.util.Color;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.minecraft.world.entity.player.Player;

public class TouhouInstincts {

    public static void registerEvents() {
        // 雾雨魔理沙看博丽灵梦飞行
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(THMagicForestRoles.KIRISAME_MARISA_ID,
                (client, self, target, hasInstinct) ->

                {
                    if (target instanceof Player targetPlayer
                            && SREClient.gameComponent.isRole(targetPlayer, THMiscRoles.HAKUREI_REIMU)) {
                        if (targetPlayer.getAbilities().flying)
                            return TrueFalseAndCustomResult.custom(Color.CYAN.getRGB());
                    }
                    return TrueFalseAndCustomResult.pass();
                });
        // 四季
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(THMiscRoles.SHIKIEIKI_ID,
                (client, self, target, hasInstinct) -> {
                    if (target instanceof Player targetPlayer) {
                        var mainhandItem = targetPlayer.getMainHandItem();
                        var offhandItem = targetPlayer.getOffhandItem();
                        if (targetPlayer.distanceToSqr(self) <= 5 * 5) {
                            if (mainhandItem.getItem() instanceof TrainWeapon
                                    || mainhandItem.is(TMMItemTags.GUNS) || mainhandItem.is(TMMItemTags.BOWS)
                                    || offhandItem.getItem() instanceof TrainWeapon
                                    || offhandItem.is(TMMItemTags.BOWS)
                                    || offhandItem.is(TMMItemTags.GUNS)) {
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
