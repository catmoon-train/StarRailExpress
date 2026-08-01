/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    public WreathItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if (!(entity instanceof Player pl)) {
            return;
        }
        ItemStack headItem = pl.getSlot(103).get();
        if (!headItem.equals(itemStack) || !itemStack.is(ModItems.WREATH)) {
            return;
        }
        // 耐久耗尽后不再提供效果（仅在物品可损坏时判定，避免未设置耐久时判定恒真）
        if (itemStack.isDamageableItem() && itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
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
        // 每秒（20 tick）消耗 1 点耐久；按游戏刻计以保证每个物品栈独立扣耐久
        if (!level.isClientSide() && itemStack.isDamageableItem()
                && !pl.isCreative() && !pl.isSpectator()
                && level.getGameTime() % 20 == 0) {
            itemStack.setDamageValue(itemStack.getDamageValue() + 1);
        }
    }
}
