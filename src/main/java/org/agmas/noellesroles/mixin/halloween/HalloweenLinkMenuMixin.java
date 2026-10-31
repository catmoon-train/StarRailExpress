package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 借用者菜单只读，禁止把镜像搬进箱子、合成栏或鼠标栈。 */
@Mixin(AbstractContainerMenu.class)
public abstract class HalloweenLinkMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void halloween$linkClick(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && HalloweenSoulLinks.isItemBorrower(sp)) {
            HalloweenSoulLinks.syncItems(sp);
            sp.containerMenu.broadcastFullState();
            ci.cancel();
        }
    }
}
