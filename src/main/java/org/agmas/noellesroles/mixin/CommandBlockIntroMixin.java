package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.register.SREEventRegister;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 命令方块局自己发开场轨道，跳过默认的俯视玩家镜头。 */
@Mixin(SREEventRegister.class)
public class CommandBlockIntroMixin {
    @Inject(method = "sendDefaultIntroIfParticipant", at = @At("HEAD"), cancellable = true)
    private static void skipWhenCommandBlock(ServerPlayer player, CallbackInfo ci) {
        if (player.level() instanceof ServerLevel level && CommandBlockDirector.replacesOpeningIntro(level)) {
            ci.cancel();
        }
    }
}
