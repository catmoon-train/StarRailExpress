package org.agmas.noellesroles.mixin.item;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.PortableIdentityScannerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在职业放行与丢弃白名单之前拦截扫描冷却，覆盖所有职业及客户端按键预测。 */
@Mixin(value = RoleMethodDispatcher.class, remap = false)
public abstract class PortableScannerDropMixin {
    @Inject(method = "callOnDropItem", at = @At("HEAD"), cancellable = true)
    private static void noellesroles$guardScannerCooldown(Player player, ItemStack stack,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!PortableIdentityScannerItem.blocksDrop(player, stack)) return;
        if (player instanceof ServerPlayer owner) {
            // 重连或暂停时客户端的原版冷却可能先结束，拒绝后补回客户端预测移走的物品。
            owner.containerMenu.sendAllDataToRemote();
            if (owner.containerMenu != owner.inventoryMenu) owner.inventoryMenu.sendAllDataToRemote();
        }
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
