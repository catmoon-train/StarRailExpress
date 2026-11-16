package io.wifi.srepokemoncompat.mixin.sweep;

import io.wifi.srepokemoncompat.sweep.SweepProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "xin.vanilla.aotake.util.EntitySweeper", remap = false)
public class AotakeRemovalMixin {
    @Inject(method = "tryScheduleRemoveEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$protectSchedule(Entity entity, boolean immediate, CallbackInfoReturnable<Boolean> cir) {
        if (SweepProtection.isProtected(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "lambda$flushPendingRemovals$2", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$protectPending(ServerLevel level, Entity entity, Boolean immediate, CallbackInfoReturnable<Boolean> cir) {
        // true 仅表示这条待删任务已完成，让 RemovalLedger 移除它；不删除实体，也不永远重试。
        if (SweepProtection.isProtected(entity)) cir.setReturnValue(true);
    }
}
