package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.OptionalInt;

/** 借用者不打开外部容器，避免菜单回收、合成或特殊按钮搬走共享物品。 */
@Mixin(ServerPlayer.class)
public abstract class HalloweenLinkContainerMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void halloween$linkContainer(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (HalloweenSoulLinks.isItemBorrower((ServerPlayer) (Object) this)) cir.setReturnValue(OptionalInt.empty());
    }
}
