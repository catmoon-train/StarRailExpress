package org.agmas.noellesroles.mixin.cca;

import io.wifi.starrailexpress.cca.PlayerMoodComponent;
import org.agmas.noellesroles.component.TemporaryEffectPlayerComponent;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerMoodComponent Mixin
 * 用于实现狗皮膏药的san值保护效果
 */
@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodComponentMixin {
    
    @Shadow
    @Final
    private Player player;
    
    /**
     * 在serverTick开始时检查狗皮膏药保护
     * 如果玩家处于保护状态，跳过san值下降
     */
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void onServerTick(CallbackInfo ci) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        
        // 检查是否受狗皮膏药保护
        TemporaryEffectPlayerComponent tempEffect = TemporaryEffectPlayerComponent.KEY.get(player);
        if (tempEffect != null && tempEffect.hasDogskinPlasterProtection()) {
            ci.cancel();
        }
    }
    
    /**
     * 在clientTick开始时检查狗皮膏药保护
     */
    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private void onClientTick(CallbackInfo ci) {
        if (player == null) {
            return;
        }
        
        // 检查是否受狗皮膏药保护
        TemporaryEffectPlayerComponent tempEffect = TemporaryEffectPlayerComponent.KEY.get(player);
        if (tempEffect != null && tempEffect.hasDogskinPlasterProtection()) {
            ci.cancel();
        }
    }
}
