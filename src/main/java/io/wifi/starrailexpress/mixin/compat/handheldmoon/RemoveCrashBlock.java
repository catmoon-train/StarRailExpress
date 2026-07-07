package io.wifi.starrailexpress.mixin.compat.handheldmoon;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.core.BlockPos;

@Mixin(targets = "com.sighs.handheldmoon.block.FullMoonBlock")
public class RemoveCrashBlock {

    @Redirect(
        method = "onRemove",
        at = @At(
            value = "INVOKE",
            target = "Lcom/sighs/handheldmoon/lights/HandheldMoonDynamicLightsInitializer;removeFullMoonBehaviorAt(Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private void redirectRemoveFullMoonBehaviorAt(BlockPos pos) {
        // 方法体留空，相当于删除该调用
    }
}
