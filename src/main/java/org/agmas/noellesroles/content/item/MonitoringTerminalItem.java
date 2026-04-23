package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.content.block_entity.SecurityMonitorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class MonitoringTerminalItem extends Item {
    private static final String MONITOR_POS_X = "monitor_pos_x";
    private static final String MONITOR_POS_Y = "monitor_pos_y";
    private static final String MONITOR_POS_Z = "monitor_pos_z";

    public MonitoringTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        list.add(Component.translatable(getDescriptionId() + ".tooltip"));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof SecurityMonitorBlockEntity)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        setBoundMonitorPos(stack, clickedPos);
        player.displayClientMessage(Component.literal("已绑定远程监控器: X=" + clickedPos.getX() + ", Y="
                + clickedPos.getY() + ", Z=" + clickedPos.getZ()).withStyle(ChatFormatting.GREEN), true);

        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        BlockPos monitorPos = getBoundMonitorPos(stack);
        if (monitorPos == null) {
            player.displayClientMessage(Component.literal("请先右键一个安全监控器进行绑定").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        boolean opened = SecurityMonitorBlock.openMonitorRemotely(serverPlayer, monitorPos);
        if (!opened) {
            return InteractionResultHolder.fail(stack);
        }

        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void setBoundMonitorPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(MONITOR_POS_X, pos.getX());
        tag.putInt(MONITOR_POS_Y, pos.getY());
        tag.putInt(MONITOR_POS_Z, pos.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static BlockPos getBoundMonitorPos(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(MONITOR_POS_X) || !tag.contains(MONITOR_POS_Y) || !tag.contains(MONITOR_POS_Z)) {
            return null;
        }
        return new BlockPos(tag.getInt(MONITOR_POS_X), tag.getInt(MONITOR_POS_Y), tag.getInt(MONITOR_POS_Z));
    }
}
