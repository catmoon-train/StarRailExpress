package org.agmas.noellesroles.content.item;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

public class WreathItem extends ArmorItem {
    private int tick = 0;

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    public WreathItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (entity instanceof Player pl) {
            ItemStack headItem = pl.getSlot(103).get();
            if (headItem.equals(itemStack) && itemStack.is(ModItems.WREATH)) {
                // 耐久耗尽后不再提供效果
                if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                    pl.removeEffect(ModEffects.MOOD_REGENERATION);
                    return;
                }
                // 持续给予 san值恢复
                pl.addEffect(new MobEffectInstance(
                        ModEffects.MOOD_REGENERATION,
                        50,
                        0,
                        true,
                        false,
                        true
                ));
                // 每秒（20 tick）消耗 1 点耐久
                this.tick++;
                if (this.tick >= 20) {
                    this.tick = 0;
                    if (!pl.isCreative() && !pl.isSpectator()) {
                        itemStack.setDamageValue(itemStack.getDamageValue() + 1);
                    }
                }
            }
        }
    }
}
