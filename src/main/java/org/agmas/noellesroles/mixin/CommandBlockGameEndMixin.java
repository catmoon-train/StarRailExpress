package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import net.minecraft.server.level.ServerLevel;

/** 事件进行中，普通阵营结算不能结束对局。时间耗尽和中立独立获胜仍走原判定。 */
@Mixin(SREMurderGameMode.class)
public class CommandBlockGameEndMixin {
    @Inject(method = "allowGameEnd", at = @At("RETURN"), cancellable = true)
    private void keepRoundOpen(ServerLevel level, GameUtils.WinStatus proposed, boolean looseEnds,
            SREGameWorldComponent game, CallbackInfoReturnable<GameUtils.WinStatus> cir) {
        GameUtils.WinStatus filtered = CommandBlockDirector.filterEnd(level, proposed, cir.getReturnValue());
        if (filtered != cir.getReturnValue()) {
            cir.setReturnValue(filtered);
        }
    }
}
