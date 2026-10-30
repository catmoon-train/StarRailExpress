package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.PumpkinHideoutRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版 SCALE 最低 0.0625；仅对据点住客修正有效模型/碰撞体型，不修改全局属性下限。 */
@Mixin(LivingEntity.class)
public abstract class PumpkinHideoutScaleMixin {
    @Inject(method = "getScale", at = @At("RETURN"), cancellable = true)
    private void halloween$hideoutScale(CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof Player player) || player.getAttributes() == null) return;
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale != null && scale.hasModifier(PumpkinHideoutRules.SCALE_MODIFIER_ID)) {
            cir.setReturnValue(Math.min(cir.getReturnValueF(), (float) PumpkinHideoutRules.SCALE));
        }
    }
}
