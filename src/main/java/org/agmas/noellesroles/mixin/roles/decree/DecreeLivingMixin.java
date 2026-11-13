package org.agmas.noellesroles.mixin.roles.decree;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.agmas.noellesroles.game.roles.innocence.decree.DecreeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 跳跃、疾跑与失明新增效果的服务端门控，客户端另有输入预测。 */
@Mixin(LivingEntity.class)
public abstract class DecreeLivingMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void decree$jump(CallbackInfo ci) {
        if ((Object)this instanceof Player player && DecreeService.isRestricted(player, Decree.MOVEMENT)) ci.cancel();
    }
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void decree$sprint(boolean sprinting, CallbackInfo ci) {
        if (sprinting && (Object)this instanceof Player player && DecreeService.isRestricted(player, Decree.SPRINT)) ci.cancel();
    }
    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void decree$sight(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if ((effect.is(MobEffects.DARKNESS) || effect.is(MobEffects.BLINDNESS))
                && (Object)this instanceof Player player && DecreeService.isRestricted(player, Decree.BLINDNESS)) {
            cir.setReturnValue(false);
        }
    }
}
