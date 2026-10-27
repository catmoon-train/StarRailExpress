package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.server.level.ServerLevel;

/** 命令方块局里，好人死亡只给对局加 20 秒，不再加平时的 30 秒。 */
@Mixin(GameMode.class)
public class CommandBlockKillTimeMixin {
    private static final int DEATH_TIME = 20 * 20;

    @Inject(method = "addKillRewardTime", at = @At("HEAD"), cancellable = true)
    private void commandBlockDeathTime(SREGameTimeComponent gameTimeComponent, CallbackInfo ci) {
        if (!(gameTimeComponent.world instanceof ServerLevel level) || !CommandBlockDirector.isActive(level)) {
            return;
        }
        if (gameTimeComponent.getTime() < gameTimeComponent.getResetTime()) {
            gameTimeComponent.addKillRewardTime(DEATH_TIME);
        }
        ci.cancel();
    }
}
