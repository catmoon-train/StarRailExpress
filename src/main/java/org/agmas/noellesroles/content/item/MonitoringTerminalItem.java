package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.content.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.util.SRENBTUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

import java.util.ArrayList;
import java.util.List;

public class MonitoringTerminalItem extends Item implements AdventureUsable {
    private static final String MONITOR_POS_FATHER = "monitor_pos";
    private static int stackIdx = 0;

    public MonitoringTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        list.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof SecurityMonitorBlockEntity)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (containsBoundMonitorPos(stack, clickedPos)) {
            removeBoundMonitorPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable("message.item.noellesroles.monitoring_terminal.remove_successfully",
                            clickedPos.toShortString()).withStyle(ChatFormatting.RED),
                    true);
        } else {
            addBoundMonitorPos(stack, clickedPos);
            player.displayClientMessage(
                    Component.translatable("message.item.noellesroles.monitoring_terminal.bind_successfully",
                            clickedPos.toShortString()).withStyle(ChatFormatting.GREEN),
                    true);
        }
        // if (SRE.REPLAY_MANAGER != null) {
        // SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
        // BuiltInRegistries.ITEM.getKey(this));
        // }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        ArrayList<BlockPos> monitorPoses = getBoundMonitorPos(stack);
        if (monitorPoses == null || monitorPoses.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.item.noellesroles.monitoring_terminal.please_bind")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (stackIdx < 0)
            stackIdx = 0;
        if (stackIdx >= monitorPoses.size())
            stackIdx = 0;
        BlockPos monitorPos = monitorPoses.get(stackIdx);
        stackIdx++;
        boolean opened = SecurityMonitorBlock.openMonitorRemotely(serverPlayer, monitorPos);
        if (!opened) {
            return InteractionResultHolder.fail(stack);
        }

//        if (SRE.REPLAY_MANAGER != null) {
//            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
//        }
        return InteractionResultHolder.consume(stack);
    }

    private static boolean containsBoundMonitorPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(MONITOR_POS_FATHER)) {
            return false;
        }
        ListTag listTag = tag.getList(MONITOR_POS_FATHER, Tag.TAG_COMPOUND);
        for (Tag t : listTag) {
            CompoundTag t1 = (CompoundTag) t;
            var ppos = SRENBTUtils.tagToBlockPos(t1);

            if (ppos == null)
                continue;
            if (pos.equals(ppos)) {
                return true;
            }
            // t1.h
        }
        return false;
    }

    private static void removeBoundMonitorPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(MONITOR_POS_FATHER)) {
            return;
        }
        ListTag listTag = tag.getList(MONITOR_POS_FATHER, Tag.TAG_COMPOUND);
        int l = -1;
        for (int i = 0; i < listTag.size(); i++) {

            CompoundTag t1 = listTag.getCompound(i);
            var ppos = SRENBTUtils.tagToBlockPos(t1);
            if (ppos == null)
                continue;
            if (pos.equals(ppos)) {
                l = i;
                break;
            }
            // t1.h
        }
        if (l >= 0) {
            listTag.remove(l);
        }
        tag.put(MONITOR_POS_FATHER, listTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void addBoundMonitorPos(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(MONITOR_POS_FATHER)) {
            tag.put(MONITOR_POS_FATHER, new ListTag());
        }
        ListTag listTag = tag.getList(MONITOR_POS_FATHER, Tag.TAG_COMPOUND);
        listTag.add(SRENBTUtils.blockPosToTag(pos));
        tag.put(MONITOR_POS_FATHER, listTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ArrayList<BlockPos> getBoundMonitorPos(ItemStack stack) {
        ArrayList<BlockPos> results = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MONITOR_POS_FATHER)) {
            var listTag = tag.getList(MONITOR_POS_FATHER, Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                var pos = SRENBTUtils.tagToBlockPos(listTag.getCompound(i));
                results.add(pos);
            }
        } else {
            return null;
        }
        return results;
    }
}
