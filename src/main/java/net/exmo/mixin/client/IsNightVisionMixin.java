package net.exmo.mixin.client;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.EXSREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class IsNightVisionMixin {
    @Inject(at = @At("HEAD"), method = "hasEffect", cancellable = true)
    public void hasEffect$sre$inject(Holder<MobEffect> holder, CallbackInfoReturnable<Boolean> cir) {
        var player = (LivingEntity) (Object) this;
        if (player instanceof LocalPlayer localPlayer) {
            LocalPlayer player1 = Minecraft.getInstance().player;
            if (player1==null)
                return;
            if (localPlayer.getUUID()== player1.getUUID() && SREClient.isInstinctEnabled()) {
                if (holder == (MobEffects.NIGHT_VISION)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
    @Inject(at = @At("HEAD"), method = "getEffect", cancellable = true)
    public void getEffect$sre$inject(Holder<MobEffect> holder, CallbackInfoReturnable<MobEffectInstance> cir) {
        var player = (LivingEntity) (Object) this;
        if (player instanceof LocalPlayer localPlayer) {
            LocalPlayer player1 = Minecraft.getInstance().player;
            if (player1==null)
                return;
            if (localPlayer.getUUID()== player1.getUUID() && SREClient.isInstinctEnabled()) {
                if (holder == (MobEffects.NIGHT_VISION)) {
                    cir.setReturnValue(EXSREClient.night_vision_cache_);
                    cir.cancel();
                }
            }
        }
    }
}
