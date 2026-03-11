package org.agmas.noellesroles.mixin.client.time_stop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class EntityCantUpdatePositionMixin extends Entity {


    public EntityCantUpdatePositionMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    public void lerpTo(double d, double e, double f, float g, float h, int i, CallbackInfo ci) {
        if (this.level().isClientSide){
            if (Minecraft.getInstance().player.hasEffect((ModEffects.TIME_STOP))){
                if (TimeStopEffect.canMovePlayers.contains(Minecraft.getInstance().player.getUUID())){
                    return;
                }
                ci.cancel();
            }
        }
    }
}
