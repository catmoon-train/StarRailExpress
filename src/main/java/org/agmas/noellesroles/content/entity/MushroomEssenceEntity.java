package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

public class MushroomEssenceEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private final boolean poisonous;

    public MushroomEssenceEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type, Level level) {
        this(type, level, false);
    }

    public MushroomEssenceEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type,
            Level level, boolean poisonous) {
        super(type, level);
        this.poisonous = poisonous;
    }

    @Override
    protected Item getDefaultItem() {
        return poisonous ? ModItems.POISONOUS_MUSHROOM_ESSENCE : ModItems.MUSHROOM_ESSENCE;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK,
                    SoundSource.NEUTRAL, 1.0F, 1.0F);
            AABB area = new AABB(getX() - 3, getY() - 2, getZ() - 3,
                    getX() + 3, getY() + 2, getZ() + 3);
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                    player -> player.isAlive() && !player.isSpectator())) {
                if (poisonous) {
                    player.addEffect(new MobEffectInstance(ModEffects.GLITCH, 20 * 20, 0, false, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 0, false, true, true));
                    player.addEffect(new MobEffectInstance(ModEffects.PUPPET_WANDER, 3 * 20, 0, false, true, true));
                } else {
                    player.addEffect(new MobEffectInstance(ModEffects.NEGATIVE_EFFECT_RESISTANCE,
                            20 * 20, 0, false, true, true));
                }
            }
            discard();
        }
    }
}
