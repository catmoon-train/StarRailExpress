package org.agmas.noellesroles.mixin.roles.decree;

import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.game.roles.innocence.decree.DecreeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 禁止背包律令不能通过其他方块或实体的容器界面绕过。 */
@Mixin(ServerPlayer.class)
public abstract class DecreeServerPlayerMixin {
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
    private void decree$open(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (DecreeService.isRestricted((ServerPlayer)(Object)this, Decree.INVENTORY)) {
            cir.setReturnValue(OptionalInt.empty());
        }
    }
}
