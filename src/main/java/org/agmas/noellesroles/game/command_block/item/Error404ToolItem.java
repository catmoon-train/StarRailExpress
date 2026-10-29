package org.agmas.noellesroles.game.command_block.item;

import java.util.List;
import org.agmas.noellesroles.game.command_block.Error404Role;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 404 道具只转发职业规则；物品消耗统一由职业服务端校验成功后执行。 */
public final class Error404ToolItem extends Item {
    public enum Tool { TERMINAL, DETONATOR, ACCELERATOR }
    private final Tool tool;
    public Error404ToolItem(Properties properties, Tool tool) { super(properties); this.tool = tool; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.fail(stack);
        boolean success = switch (tool) {
            case TERMINAL -> Error404Role.openTerminal(serverPlayer);
            case DETONATOR -> Error404Role.useDetonator(serverPlayer);
            case ACCELERATOR -> Error404Role.useAccelerator(serverPlayer);
        };
        return success ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
