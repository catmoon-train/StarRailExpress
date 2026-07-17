package org.agmas.noellesroles.content.item;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * 攀登靴
 * - 穿在脚上（渲染为皮革靴子）
 * - 在海上时可以登上比海平面高一格的方块到达上面
 */
public class ClimbingBootsItem extends ArmorItem {

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    public ClimbingBootsItem(Holder<ArmorMaterial> holder, Type type, Properties properties) {
        super(holder, type, properties);
    }
}
