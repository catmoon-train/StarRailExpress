package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.exmo.sre.planecrash.CrashPlaneEntity;
import net.exmo.sre.planecrash.PlaneCrashManager;
import net.minecraft.server.level.ServerLevel;

/** 命令方块局自己发开场轨道，飞机聚焦包不要再插进来。 */
@Mixin(PlaneCrashManager.class)
public class CommandBlockPlaneIntroMixin {
    @Inject(method = "sendIntro", at = @At("HEAD"), cancellable = true)
    private static void skipPlaneCamera(ServerLevel level, CrashPlaneEntity plane, CallbackInfo ci) {
        if (CommandBlockDirector.replacesOpeningIntro(level)) {
            ci.cancel();
        }
    }
}
