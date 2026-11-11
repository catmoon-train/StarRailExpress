package org.agmas.noellesroles.mixin.item;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.PortableIdentityScannerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 背包丢单件/整组和鼠标移出界面丢弃，均在物品被移走前执行同一丢弃规则。 */
@Mixin(AbstractContainerMenu.class)
public abstract class PortableScannerMenuDropMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void noellesroles$guardScannerMenuDrop(int slot, int button, ClickType action, Player player, CallbackInfo ci) {
        if (player.isCreative()) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ItemStack stack;
        if (action == ClickType.THROW && slot >= 0 && slot < menu.slots.size()) {
            stack = menu.getSlot(slot).getItem();
        } else if (action == ClickType.PICKUP && slot == -999) {
            stack = menu.getCarried();
        } else return;
        if (!(stack.getItem() instanceof PortableIdentityScannerItem)) return;
        // 复用普通丢弃入口，也保留验尸官原有的首次150秒限制。
        InteractionResult result = RoleMethodDispatcher.callOnDropItem(player, stack);
        if (result == InteractionResult.FAIL || result == InteractionResult.CONSUME
                || result == InteractionResult.CONSUME_PARTIAL) {
            if (player instanceof ServerPlayer) menu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
