package io.wifi.srepackcompat.mixin.server;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 无乘客时省去间接乘客流及迭代器；骑乘链和服务端距离倍率仍由原版处理。 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityRangeMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private int range;
    @Shadow protected abstract int scaledRange(int range);

    @Inject(method = "getEffectiveRange", at = @At("HEAD"), cancellable = true)
    private void sre$emptyPassengerRange(CallbackInfoReturnable<Integer> cir) {
        if (entity.getPassengers().isEmpty()) cir.setReturnValue(scaledRange(range));
    }
}
