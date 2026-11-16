package io.wifi.srepokemoncompat.mixin.sweep;

import io.wifi.srepokemoncompat.sweep.SweepProtection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在回收提交之前否决，交给原提交器正常计入 skipped，尚不触碰捕获物品或实体 NBT。 */
@Pseudo
@Mixin(targets = "xin.vanilla.aotake.util.EntitySweeper$RecyclingBatch$1", remap = false)
public class AotakeRecyclingMixin {
    @Inject(method = "eligible(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$protectRecycling(Object candidate, CallbackInfoReturnable<Boolean> cir) {
        if (candidate instanceof Entity entity && SweepProtection.isProtected(entity)) cir.setReturnValue(false);
    }
}
