package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.command.PaintCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 同 {@link GameCommandMixin}：{@code /mecchapaint} 也不对外开放。 */
@Mixin(value = PaintCommand.class, remap = false)
public abstract class PaintCommandMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void sre$disableChameleonPaintCommand(CommandDispatcher<CommandSourceStack> dispatcher,
            CallbackInfo ci) {
        ci.cancel();
    }
}
