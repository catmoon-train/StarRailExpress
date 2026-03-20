package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ToiletPoisonMixin {

    @Unique
    private Scheduler.ScheduledTask sre$poisonToiletTask = null;

    @Inject(method = "stopRiding", at = @At("HEAD"))
    public void sre$cancelToiletPoisonTask(CallbackInfo ci) {
        // 玩家离开座位时取消中毒任务
        if (this.sre$poisonToiletTask != null) {
            this.sre$poisonToiletTask.cancel();
            this.sre$poisonToiletTask = null;
        }
    }
}
