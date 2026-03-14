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
