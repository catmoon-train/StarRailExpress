package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 高于现有导演、安全摄像头和职业相机，在 HEAD 完整初始化后直接返回。 */
@Mixin(value = Camera.class, priority = 2500)
public abstract class SettlementCameraMixin {
    @Shadow private boolean initialized;
    @Shadow private BlockGetter level;
    @Shadow private Entity entity;
    @Shadow private boolean detached;
    @Shadow private float partialTickTime;

    @Inject(method = "setup", at = @At("HEAD"), cancellable = true)
    private void settlement$camera(BlockGetter world, Entity entity, boolean detached, boolean reverse,
                                   float partial, CallbackInfo ci) {
        if (!SettlementClient.isActive()) return;
        this.initialized = true; this.level = world; this.entity = entity;
        this.detached = true; this.partialTickTime = partial;
        SettlementClient.applyCamera((Camera) (Object) this, partial);
        ci.cancel();
    }
}
