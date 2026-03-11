package org.agmas.harpymodloader.mixin;

import io.wifi.starrailexpress.api.GameMode;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.command.StartCommand;
import net.minecraft.commands.CommandSourceStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StartCommand.class)
public class VannilaStartMixin {

    @Inject(method = "execute", at = @At("HEAD"))
    private static void a(CommandSourceStack source, GameMode gameMode, int minutes, CallbackInfoReturnable<Integer> cir) {
        if (gameMode.equals(SREGameModes.MURDER)) Harpymodloader.wantsToStartVannila = true;
     }
}
