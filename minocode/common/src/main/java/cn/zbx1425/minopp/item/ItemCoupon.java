package cn.zbx1425.minopp.item;

import cn.zbx1425.minopp.platform.GroupedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ItemCoupon extends GroupedItem {

    public ItemCoupon() {
        super(() -> null, p -> p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("item.minopp.coupon.description"));
    }
}
