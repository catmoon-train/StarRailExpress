package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties.LeftClickHurtable;
import io.wifi.starrailexpress.game.modes.funny.SRETNTTagGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HotPotatoItem extends Item implements LeftClickHurtable {

    public HotPotatoItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (!level.isClientSide && level.getGameTime() % 20 == 0) {
            if (entity instanceof ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        (int) (30), // 持续时间（tick）
                        2, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        false // showIcon（显示图标）
                ));
            }
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer from_player && target instanceof ServerPlayer to_player) {
            SRETNTTagGameMode.transformTNTTag(from_player, to_player);
        }
        return true;
    }

}
