package org.agmas.noellesroles.mixin.roles.ma_chen_xu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BundleItem;
import org.agmas.noellesroles.content.item.GhostArtItem;
import org.agmas.noellesroles.content.item.GhostWardRelicItem;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 诡物不进入外部容器；法器不能借容器点击绕过好人资格，且不允许创造复制。 */
@Mixin(AbstractContainerMenu.class)
public abstract class GhostArtContainerMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void noellesroles$guardGhostTransfer(int slot, int button, ClickType action, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ItemStack clicked = slot >= 0 && slot < menu.slots.size() ? menu.getSlot(slot).getItem() : ItemStack.EMPTY;
        ItemStack carried = menu.getCarried();
        ItemStack swapped = action == ClickType.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
                ? player.getInventory().getItem(button) : ItemStack.EMPTY;
        boolean art = clicked.getItem() instanceof GhostArtItem || carried.getItem() instanceof GhostArtItem || swapped.getItem() instanceof GhostArtItem;
        boolean relic = clicked.getItem() instanceof GhostWardRelicItem || carried.getItem() instanceof GhostWardRelicItem || swapped.getItem() instanceof GhostWardRelicItem;
        boolean bundleTransfer = (art || relic)
                && (clicked.getItem() instanceof BundleItem || carried.getItem() instanceof BundleItem);
        if ((art && menu != player.inventoryMenu) || bundleTransfer
                || ((art || relic) && action == ClickType.CLONE)
                || (relic && !SackGhostItems.eligibleRelicCarrier(player) && action != ClickType.THROW)) ci.cancel();
    }
}
