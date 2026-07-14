package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsBreakIn;
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

import java.util.List;

/**
 * 闯入道具：撬棍（alarms=true，强闯并报警）/ 撬锁器（alarms=false，潜行不报警）。
 * 分 1~3 级（{@code tier}），只能闯入门等级不高于工具等级的队伍。右键使用，<b>一次性消耗</b>。
 */
public class SixtySecondsBreakInItem extends Item {
    private final boolean alarms;
    private final int tier;

    /** 兼容旧注册：默认 1 级。 */
    public SixtySecondsBreakInItem(Properties properties, boolean alarms) {
        this(properties, alarms, 1);
    }

    public SixtySecondsBreakInItem(Properties properties, boolean alarms, int tier) {
        super(properties);
        this.alarms = alarms;
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public boolean alarms() {
        return alarms;
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
        // 不再远程开选队界面——闯入改走统一门菜单：在探索区对着别队的避难所门右键，
        // 菜单里选「强闯/潜入」（SixtySecondsDoorMenu → SixtySecondsBreakIn.executeAtDoor）
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.breakin_use_on_door"), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.breakin_tier", tier)
                .withStyle(ChatFormatting.GRAY));
    }
}
