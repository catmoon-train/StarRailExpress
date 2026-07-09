package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.game.Room;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 回合的结束只能由 SRE 宣布。
 *
 * <p>
 * {@code Room.onShot} 在所有猎人打空弹药时会直接调用 {@code Room.end}，把玩家传送回去、
 * 还原游戏模式、清掉旁观状态——那会在 SRE 的局中途炸掉整场游戏。这里把它按住。
 * </p>
 */
@Mixin(value = Room.class, remap = false)
public abstract class RoomGuardMixin {

    @Inject(method = "end", at = @At("HEAD"), cancellable = true)
    private void sre$blockChameleonRoundEnd(MinecraftServer server, boolean natural, CallbackInfo ci) {
        if (ChameleonCompat.ownsRoom(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true)
    private void sre$blockChameleonRoundStop(MinecraftServer server, CallbackInfo ci) {
        if (ChameleonCompat.ownsRoom(this)) {
            ci.cancel();
        }
    }
}
