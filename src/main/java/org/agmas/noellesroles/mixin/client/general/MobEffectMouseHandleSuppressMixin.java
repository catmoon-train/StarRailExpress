package org.agmas.noellesroles.mixin.client.general;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MobEffectMouseHandleSuppressMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void noe$restrainMouse(double d, CallbackInfo ci) {

        if (Minecraft.getInstance() == null)
            return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (player.hasEffect(ModEffects.TURN_BANED)){
            ci.cancel();
        }
    }
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void noe$restrainScroll(long l, double d, double e, CallbackInfo ci) {

        if (Minecraft.getInstance() == null)
            return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (player.hasEffect(ModEffects.TURN_BANED)){
            ci.cancel();
        }
    }
}
