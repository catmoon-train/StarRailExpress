package org.agmas.noellesroles.game.halloween.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.halloween.HalloweenConsumables;

import java.util.List;

/** 橙色皮革外观的活动套装；不提供常驻护甲属性，抵挡逻辑由套装整体处理。 */
public final class PumpkinCostumeItem extends ArmorItem {
    public PumpkinCostumeItem(Type type) {
        super(ArmorMaterials.LEATHER, type, new Properties().stacksTo(1)
                .component(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .component(DataComponents.DYED_COLOR, new DyedItemColor(0xE77918, false)));
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && !HalloweenConsumables.canUse(player)) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.noellesroles.halloween.costume").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.noellesroles.halloween.only_event").withStyle(ChatFormatting.DARK_GRAY));
    }
}
