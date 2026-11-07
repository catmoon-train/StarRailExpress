package io.wifi.srepackcompat.mixin.chloride;

import net.minecraft.client.gui.components.CycleButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 粒子列表不经过 Sodium builder；保留列表可读，但禁止修改逐类型开关。 */
@Pseudo
@Mixin(targets = "me.srrapero720.chloride.impl.sodium.ParticleListScreen$ParticleList$Entry", remap = false)
public abstract class ChlorideParticleToggleMixin {
    @Shadow @Final private CycleButton<Boolean> toggle;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sreCompat$lockToggle(CallbackInfo ci) { toggle.active = false; }
}
