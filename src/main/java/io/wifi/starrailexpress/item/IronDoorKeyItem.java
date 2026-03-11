package io.wifi.starrailexpress.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public class IronDoorKeyItem extends Item {
    private static final int MAX_USES = 3;

    public IronDoorKeyItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int usesLeft = MAX_USES - stack.getDamageValue();
        if (usesLeft > 0) {
            tooltipComponents.add(Component.translatable("tip.iron_door_key.uses_left", usesLeft));
        } else {
            tooltipComponents.add(Component.translatable("tip.iron_door_key.broken"));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
