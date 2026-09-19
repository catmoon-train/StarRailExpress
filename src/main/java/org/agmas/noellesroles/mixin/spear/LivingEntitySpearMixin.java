package org.agmas.noellesroles.mixin.spear;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.spear.SpearCombat;
import org.agmas.noellesroles.spear.SpearConfig;
import org.agmas.noellesroles.spear.SpearUser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 让所有生物都能使用「矛」：记录蓄力冲锋的接触冷却与上一次冲锋命中的时间。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySpearMixin implements SpearUser {

    @Unique
    private long spear$lastKineticAttackTime = -1L;
    @Unique
    private final Map<Entity, Long> spear$piercingCooldowns = new HashMap<>();

    /** 使用矛自身的 23 tick 挥击时长，而不是原版默认的 6 tick。 */
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void spear$useLongerSwing(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (SpearConfig.isSpear(self.getMainHandItem()) || SpearConfig.isSpear(self.getOffhandItem())) {
            cir.setReturnValue(SpearConfig.SWING.swingTicks());
        }
    }

    @Inject(method = "startUsingItem", at = @At("HEAD"))
    private void spear$startCharge(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (SpearConfig.isSpear(self.getItemInHand(hand))) {
            this.spear$piercingCooldowns.clear();
            SpearConfig.kinetic().playSound(self);
        }
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void spear$stopCharge(CallbackInfo ci) {
        this.spear$piercingCooldowns.clear();
    }

    // require = 0：该注入只用于「冲锋命中后收招动画」的时间记录，找不到目标也不该让游戏崩
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void spear$markKineticAttack(byte id, CallbackInfo ci) {
        if (id == 2) {
            this.spear$lastKineticAttackTime = ((LivingEntity) (Object) this).level().getGameTime();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void spear$cleanCooldowns(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || this.spear$piercingCooldowns.isEmpty()) {
            return;
        }
        long now = self.level().getGameTime();
        // 10 秒后清理，避免长局累积
        this.spear$piercingCooldowns.values().removeIf(time -> now - time > 200L);
    }

    @Override
    public float getTimeSinceLastKineticAttack(float tickDelta) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.spear$lastKineticAttackTime < 0L) {
            return 0.0F;
        }
        return (float) (self.level().getGameTime() - this.spear$lastKineticAttackTime) + tickDelta;
    }

    @Override
    public boolean isInPiercingCooldown(Entity target, int cooldownTicks) {
        Long time = this.spear$piercingCooldowns.get(target);
        if (time == null) {
            return false;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        return self.level().getGameTime() - time < cooldownTicks;
    }

    @Override
    public void startPiercingCooldown(Entity target) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.spear$piercingCooldowns.put(target, self.level().getGameTime());
    }

    @Override
    public boolean pierce(EquipmentSlot slot, Entity target, float damage, boolean dealDamage, boolean knockback,
            boolean dismount) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack stack = self.getItemBySlot(slot);
        if (!SpearConfig.isSpear(stack)) {
            return false;
        }
        return SpearCombat.pierce(self, slot, target, damage, dealDamage, knockback, dismount);
    }
}
