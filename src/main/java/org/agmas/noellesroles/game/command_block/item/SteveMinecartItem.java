package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.SteveMiner;

import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** 冒险模式也可在铁轨上放置的原版矿车。 */
public class SteveMinecartItem extends MinecartItem implements AdventureUsable {
    public SteveMinecartItem(Properties properties) {
        super(AbstractMinecart.Type.RIDEABLE, properties);
    }

    @Override
    public boolean canUseOn(UseOnContext context, ItemStack stack) {
        return SteveMiner.canAct(context.getPlayer());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return SteveMiner.canAct(context.getPlayer()) ? super.useOn(context) : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
