package io.wifi.starrailexpress.mixin.entity;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

@Mixin(WindCharge.class)
public class WindChargeMixin {
    @Unique
    private static ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(false,
            true, Optional.of(1.22F),
            BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    protected void explode(Vec3 vec3, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        WindCharge windCharge = (WindCharge) (Object) this;
        Entity owner = windCharge.getOwner();
        DamageSource damageSource = null;
        if (owner instanceof LivingEntity le) {
            damageSource = windCharge.damageSources().windCharge(windCharge, le);
        }
        windCharge.level().explode(windCharge, damageSource, EXPLOSION_DAMAGE_CALCULATOR, vec3.x(), vec3.y(), vec3.z(),
                1.2F, false, ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.WIND_CHARGE_BURST);
        ci.cancel();
    }
}