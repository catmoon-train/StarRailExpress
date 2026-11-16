package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 不注销命令，仅阻止执行，避免通过聊天路标链接绕过禁用。 */
@Pseudo
@Mixin(targets = "journeymap.client.command.ClientCommandInvoker", remap = false)
public class ClientCommandMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$requireServerPermission(CallbackInfoReturnable<Integer> cir) {
        if (!CobblemonPolicy.enabled()) cir.setReturnValue(0);
    }
}
