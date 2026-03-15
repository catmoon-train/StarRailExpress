package org.agmas.noellesroles.item;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

/**
 * 潜水头盔
 * - 穿在头部（渲染为钻石头盔）
 * - 提供水下呼吸和海豚的恩惠1效果
 * - 可以丢出给其他人使用
 */
public class DivingHelmetItem extends ArmorItem {

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_DIAMOND;
    }

    public DivingHelmetItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player pl) {
            ItemStack headItem = pl.getSlot(103).get();
            if (headItem.equals(itemStack) && itemStack.is(ModItems.DIVING_HELMET)) {
                // 持续给予水下呼吸和海豚的恩惠效果
                pl.addEffect(new MobEffectInstance(
                        MobEffects.WATER_BREATHING,
                        50,
                        0, // 等级
                        true, // ambient
                        false, // showParticles
                        false // showIcon
                ));
                pl.addEffect(new MobEffectInstance(
                        MobEffects.DOLPHINS_GRACE,
                        50, 
                        0, // 等级（海豚恩惠1）
                        true, // ambient
                        false, // showParticles
                        false // showIcon
                ));
            }
        }
    }
}
