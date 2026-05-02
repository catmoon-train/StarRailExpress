package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.block.DNFTaskPointBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class DNFTaskToolItem extends Item {
    public DNFTaskToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !DNF.isDayNightFightMode(world)) {
            return InteractionResultHolder.pass(stack);
        }
        DNFTaskPointBlock taskPoint = getLookedAtTaskPoint(serverPlayer);
        if (taskPoint == null) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.task_point.no_target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }
        serverPlayer.startUsingItem(hand);
        world.playSound(null, serverPlayer.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.6f, 0.9f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (world.isClientSide || !(user instanceof ServerPlayer player) || !DNF.isDayNightFightMode(world)) {
            return;
        }
        int chargedTicks = getUseDuration(stack, user) - remainingUseTicks;
        if (chargedTicks < DNF.CLEANING_TICKS) {
            player.displayClientMessage(Component.translatable("message.dnf.task_point.charge_cancelled")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (hit == null || !(world.getBlockState(hit.getBlockPos()).getBlock() instanceof DNFTaskPointBlock block)) {
            player.displayClientMessage(Component.translatable("message.dnf.task_point.no_target")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        block.completeChargedTask(player, hit.getBlockPos());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        DNFItems.appendDnfTooltip(stack, context, tooltip, flag, "item.starrailexpress.dnf_task_tool.tooltip");
    }

    private static DNFTaskPointBlock getLookedAtTaskPoint(ServerPlayer player) {
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (hit == null) {
            return null;
        }
        return player.level().getBlockState(hit.getBlockPos()).getBlock() instanceof DNFTaskPointBlock block
                ? block
                : null;
    }
}
