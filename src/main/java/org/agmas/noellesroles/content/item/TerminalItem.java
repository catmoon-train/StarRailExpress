package org.agmas.noellesroles.content.item;

import java.util.List;

import org.agmas.noellesroles.role.bouns.roles.ProgrammerRole;
import org.agmas.noellesroles.utils.OpenScreenManager;

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

/**
 * 终端：程序员专属消耗品。
 *
 * <p>
 * 右键打开终端界面（界面由服务端经 {@link OpenScreenManager} 下发，见 ClientOpenScreenManager）。
 * 本类只负责「能不能开」与「开哪个界面」，指令的解析与执行都在
 * {@link org.agmas.noellesroles.role.bouns.roles.ProgrammerRole} 里。
 */
public class TerminalItem extends Item {

    public TerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!ProgrammerRole.canUseTerminal(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.terminal.spectator")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        if (ProgrammerRole.isTerminalOnCooldown(player)) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.terminal.cooldown",
                            ProgrammerRole.getTerminalCooldownSecondsLeft(player)).withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            OpenScreenManager.openScreen(serverPlayer, OpenScreenManager.PROGRAMMER_TERMINAL_SCREEN);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
