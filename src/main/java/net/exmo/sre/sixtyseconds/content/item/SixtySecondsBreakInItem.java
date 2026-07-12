package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsBreakIn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 闯入道具：撬棍（alarms=true，强闯并报警）/ 撬锁器（alarms=false，潜行不报警）。右键使用，<b>一次性消耗</b>。
 */
public class SixtySecondsBreakInItem extends Item {
    private final boolean alarms;

    public SixtySecondsBreakInItem(Properties properties, boolean alarms) {
        super(properties);
        this.alarms = alarms;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (SixtySecondsBreakIn.use(serverPlayer, alarms)) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }
}
