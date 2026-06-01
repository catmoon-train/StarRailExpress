package io.wifi.starrailexpress.mixin.entity.living;

import io.wifi.starrailexpress.cca.SREPlayerDamageTrackerComponent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    public void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 只追踪玩家的伤害
        if (!((Object) this instanceof Player player)) {
            return;
        }

        // 检查是否是另一个玩家造成的伤害
        Entity attacker = source.getEntity();
        boolean isPlayerDamage = attacker instanceof Player;

        // 记录伤害
        SREPlayerDamageTrackerComponent.recordDamage(
                player,
                isPlayerDamage,
                isPlayerDamage ? attacker.getUUID() : null
        );
    }
}
