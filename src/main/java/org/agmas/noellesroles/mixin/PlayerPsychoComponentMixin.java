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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerPsychoComponentMixin
 * - 在疯狂模式停止时，清除魔术师的假球棒
 */
@Mixin(SREPlayerPsychoComponent.class)
public class PlayerPsychoComponentMixin {

    /**
     * 拦截stopPsycho方法
     * 当疯狂模式停止时，如果玩家是魔术师，也清除假球棒
     */
    @Inject(method = "stopPsycho", at = @At("TAIL"))
    private void noellesroles$clearFakeBatWhenPsychoEnds(CallbackInfoReturnable<Integer> cir) {
        SREPlayerPsychoComponent psychoComponent = (SREPlayerPsychoComponent) (Object) this;
        var player = psychoComponent.getPlayer();
        
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        var magicianRole = TMMRoles.ROLES.get(ModRoles.MAGICIAN_ID);
        
        // 检查是否是魔术师
        if (magicianRole != null && gameWorld.isRole(player, magicianRole)) {
            // 清除假球棒
            player.getInventory().clearOrCountMatchingItems(itemStack -> itemStack.is(ModItems.FAKE_BAT), Integer.MAX_VALUE,
                    player.inventoryMenu.getCraftSlots());
        }
    }
}
