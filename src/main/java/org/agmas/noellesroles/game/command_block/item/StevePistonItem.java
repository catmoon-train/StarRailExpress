package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.SteveMiner;

import io.wifi.starrailexpress.api.RoleSkill;
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

/** 活塞只是建筑技能的物品入口，成功造墙才消耗，与技能键共享冷却与禁用校验。 */
public class StevePistonItem extends Item {
    public StevePistonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SteveMiner.canAct(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer server
                && RoleSkill.beginUse(server, null, 0, RoleSkill.Phase.PRESS)) {
            stack.consume(1, player);
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
