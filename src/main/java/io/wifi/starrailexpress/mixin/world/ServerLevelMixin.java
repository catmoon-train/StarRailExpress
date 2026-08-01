package io.wifi.starrailexpress.mixin.world;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "tickTime", at = @At("HEAD"), require = 0, cancellable = true)
    private void tickTime(CallbackInfo ci) {
        SREGameTimeComponent cca = SREGameTimeComponent.KEY.getNullable((Object) this);
        if (cca != null) {
            if (cca.levelGameTimeFrozen) {
                ci.cancel();
            }
        }
    }
    // protected void tickTime() {
    // if (this.tickTime) {
    // long l = this.levelData.getGameTime() + 1L;
    // this.serverLevelData.setGameTime(l);
    // this.serverLevelData.getScheduledEvents().tick(this.server, l);
    // if (this.levelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
    // this.setDayTime(this.levelData.getDayTime() + 1L);
    // }

    // }
    // }
}
