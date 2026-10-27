package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.agmas.harpymodloader.commands.RoleCountManager;

/** 命令方块占满杀手名额后，分配阶段不再给出杀手位。 */
@Mixin(RoleCountManager.class)
public class CommandBlockKillerCountMixin {
    @Inject(method = "getKillerCount", at = @At("HEAD"), cancellable = true)
    private static void occupyKillerSlots(int playerCount, CallbackInfoReturnable<Integer> cir) {
        if (CommandBlockDirector.occupiesKillerSlots()) {
            cir.setReturnValue(0);
        }
    }
}
