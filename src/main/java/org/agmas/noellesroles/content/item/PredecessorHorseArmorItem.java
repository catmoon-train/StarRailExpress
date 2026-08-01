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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 前人留下的马铠
 * - 继承动物马铠（AnimalArmorItem），材质沿用钻石马铠（物品图标与马身护甲层均为钻石外观）
 * - 装备到残月萨马 / 彩虹马时：移动速度 +12%，生命上限 +4
 * - 不提供原版护甲值（protection = 0），仅提供上述属性加成
 */
public class PredecessorHorseArmorItem extends AnimalArmorItem {

    public PredecessorHorseArmorItem(Item.Properties properties) {
        super(ArmorMaterials.DIAMOND, AnimalArmorItem.BodyType.EQUESTRIAN, false, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.predecessor_horse_armor.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
