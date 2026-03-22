package org.agmas.noellesroles.item;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

/**
 * 潜水靴
 * - 穿在脚上（渲染为金靴子）
 * - 自带深海探索者3附魔
 */
public class DivingBootsItem extends ArmorItem {

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GOLD;
    }

    public DivingBootsItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        // 参考RiptideTridentMixin的实现方式
        // 需要在物品被使用时动态添加附魔，或者使用Mixin方式
        // 暂时返回未附魔的物品，在Mixin或装备时添加附魔
        return stack;
    }
}
