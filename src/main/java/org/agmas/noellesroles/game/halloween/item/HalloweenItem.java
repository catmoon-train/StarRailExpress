package org.agmas.noellesroles.game.halloween.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.halloween.HalloweenConsumables;
import org.agmas.noellesroles.game.halloween.PumpkinHideouts;

import java.util.List;

/** 只负责本模块的右键消耗品；武器和魂契均为无动作的独立注册物品。 */
public class HalloweenItem extends Item {
    public enum Action { NONE, PRESIDENT, HEART, COIN, CANDY, BAG }
    private final Action action;

    public HalloweenItem(Properties properties, Action action) {
        super(properties);
        this.action = action;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (action == Action.NONE) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp) || !HalloweenConsumables.canUse(sp)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean used = action == Action.PRESIDENT ? PumpkinHideouts.place(sp, null)
                : HalloweenConsumables.use(sp, action);
        if (!used) return InteractionResultHolder.fail(stack);
        if (action == Action.CANDY)
            org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onCandyConsumed(sp, stack);
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 5);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (!level.isClientSide && this == org.agmas.noellesroles.game.halloween.HalloweenItems.BONE_SWORD)
            org.agmas.noellesroles.game.halloween.HalloweenCombat.migrateBoneDurability(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (action == Action.NONE) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player) || !HalloweenConsumables.canUse(player)) {
            return InteractionResult.FAIL;
        }
        if (action != Action.PRESIDENT) return use(context.getLevel(), player, context.getHand()).getResult();
        if (!PumpkinHideouts.place(player, context.getClickedPos().relative(context.getClickedFace()))) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        player.getCooldowns().addCooldown(this, 5);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.noellesroles.halloween.only_event").withStyle(ChatFormatting.DARK_GRAY));
    }
}
