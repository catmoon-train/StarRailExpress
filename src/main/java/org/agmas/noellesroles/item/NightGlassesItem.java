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

public class NightGlassesItem extends ArmorItem {
    private int tick = 0;

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_WOLF;
    }

    public NightGlassesItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player pl) {
            ItemStack headItem = pl.getSlot(103).get();
            if (headItem.equals(itemStack) && itemStack.is(ModItems.NIGHT_VISION_GLASSES)) {
                this.tick++;
                if (this.tick >= 20) {
                    this.tick = 0;
                    if (!pl.isCreative() && !pl.isSpectator()) {
                        itemStack.setDamageValue(itemStack.getDamageValue() + 1);
                    }
                    if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                        // itemStack.consume(1, pl);
                        pl.removeEffect(MobEffects.NIGHT_VISION);
                        return;
                    }
                    pl.addEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION, // ID
                            240, // 持续时间（tick）
                            0, // 等级（0 = 速度 I）
                            false, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            false // showIcon（显示图标）
                    ));
                }
            }
        }
    }

}
