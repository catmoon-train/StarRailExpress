package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block.ZiplineBlock;
import io.wifi.starrailexpress.content.block.entity.RemoteRedstoneBlockEntity;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.ZiplineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BindingToolItem extends Item {
    private BlockPos lastCameraPos = null;
    private BlockPos lastReactorPos = null;
    private BlockPos lastWaterValvePos = null;
    private BlockPos lastDebrisPilePos = null;
    private BlockPos lastZiplinePos = null;

    public BindingToolItem(Properties settings) {
        super(settings);
    }

    private InteractionResult handleReactorBind(net.minecraft.server.level.ServerLevel level,
            org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity reactor, BlockPos pos, Player player) {
        if (lastReactorPos == null || lastReactorPos.equals(pos)) {
            lastReactorPos = pos;
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.reactor.first_selected").withStyle(ChatFormatting.AQUA),
                    true);
            return InteractionResult.SUCCESS;
        }
        // 绑定两个反应堆为配对关系
        BlockEntity firstBe = level.getBlockEntity(lastReactorPos);
        reactor.setPartnerPos(lastReactorPos);
        if (firstBe instanceof org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity first) {
            first.setPartnerPos(pos);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.reactor.pair_bound").withStyle(ChatFormatting.GREEN),
                true);
        lastReactorPos = null;
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleWaterValveBind(net.minecraft.server.level.ServerLevel level,
            org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity valve, BlockPos pos, Player player) {
        if (lastWaterValvePos == null || lastWaterValvePos.equals(pos)) {
            lastWaterValvePos = pos;
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.water_valve.first_selected").withStyle(ChatFormatting.AQUA),
                    true);
            return InteractionResult.SUCCESS;
        }
        BlockEntity firstBe = level.getBlockEntity(lastWaterValvePos);
        valve.setPartnerPos(lastWaterValvePos);
        if (firstBe instanceof org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity first) {
            first.setPartnerPos(pos);
        }
        player.displayClientMessage(
                Component.translatable("message.noellesroles.water_valve.pair_bound").withStyle(ChatFormatting.GREEN),
                true);
        lastWaterValvePos = null;
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleDebrisPileBind(net.minecraft.server.level.ServerLevel level,
            org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity pile, BlockPos pos, Player player) {
        if (player.isShiftKeyDown() && lastDebrisPilePos != null && lastDebrisPilePos.equals(pos)) {
            lastDebrisPilePos = null;
            player.displayClientMessage(Component.translatable("message.noellesroles.debris_pile.selection_cleared")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        if (lastDebrisPilePos == null || lastDebrisPilePos.equals(pos)) {
            lastDebrisPilePos = pos;
            player.displayClientMessage(Component.translatable("message.noellesroles.debris_pile.first_selected")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }
        BlockEntity firstBe = level.getBlockEntity(lastDebrisPilePos);
        if (firstBe instanceof org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity first) {
            java.util.Set<BlockPos> group = new java.util.HashSet<>();
            group.add(lastDebrisPilePos.immutable());
            group.add(pos.immutable());
            group.addAll(first.linked());
            group.addAll(pile.linked());
            for (BlockPos groupPos : group) {
                BlockEntity be = level.getBlockEntity(groupPos);
                if (be instanceof org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity linkedPile) {
                    for (BlockPos target : group) {
                        linkedPile.addLinked(target);
                    }
                }
            }
            player.displayClientMessage(Component.translatable("message.noellesroles.debris_pile.group_bound")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        lastDebrisPilePos = pos;
        player.displayClientMessage(Component.translatable("message.noellesroles.debris_pile.first_selected")
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * 滑索柱连线：右键第一根柱子选中，右键第二根柱子连上（已连上则断开）。
     * 潜行右键清空该柱子的全部连接。手动连线可以跨高度、可以斜拉。
     */
    private InteractionResult handleZiplineBind(Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown()) {
            ZiplineBlock.unlinkAll(level, pos);
            lastZiplinePos = null;
            player.displayClientMessage(
                    Component.translatable("message.item.starrailexpress.binding_tool.zipline_cleared")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            return InteractionResult.SUCCESS;
        }

        if (lastZiplinePos == null || lastZiplinePos.equals(pos)) {
            lastZiplinePos = lastZiplinePos == null ? pos.immutable() : null;
            player.displayClientMessage(
                    Component.translatable(lastZiplinePos == null
                            ? "message.item.starrailexpress.binding_tool.zipline_unselected"
                            : "message.item.starrailexpress.binding_tool.zipline_first")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(lastZiplinePos) instanceof ZiplineBlockEntity firstZbe)) {
            lastZiplinePos = pos.immutable();
            player.displayClientMessage(
                    Component.translatable("message.item.starrailexpress.binding_tool.zipline_first")
                            .withStyle(ChatFormatting.AQUA),
                    true);
            return InteractionResult.SUCCESS;
        }

        if (!lastZiplinePos.closerThan(pos, ZiplineBlock.MAX_LINK_DISTANCE)) {
            player.displayClientMessage(
                    Component.translatable("message.item.starrailexpress.binding_tool.zipline_too_far",
                            ZiplineBlock.MAX_LINK_DISTANCE).withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.SUCCESS;
        }

        boolean connected = firstZbe.hasConnection(pos);
        if (connected) {
            ZiplineBlock.unlink(level, lastZiplinePos, pos);
        } else {
            ZiplineBlock.link(level, lastZiplinePos, pos);
        }
        player.displayClientMessage(
                Component.translatable(connected
                        ? "message.item.starrailexpress.binding_tool.zipline_unbound"
                        : "message.item.starrailexpress.binding_tool.zipline_bound",
                        lastZiplinePos.toShortString(), pos.toShortString())
                        .withStyle(connected ? ChatFormatting.YELLOW : ChatFormatting.GREEN),
                true);
        lastZiplinePos = null;
        return InteractionResult.SUCCESS;
    }

    public static BlockPos CalcRelativePosition(BlockPos from, BlockPos to) {
        var x1 = from.getX();
        var x2 = to.getX();
        var y1 = from.getY();
        var y2 = to.getY();
        var z1 = from.getZ();
        var z2 = to.getZ();
        return new BlockPos(x2 - x1, y2 - y1, z2 - z1);
    }

    // @Override
    // public void appendHoverText(ItemStack itemStack, TooltipContext
    // tooltipContext, List<Component> list,
    // TooltipFlag tooltipFlag) {
    // list.add(Component.translatable(getDescriptionId() + ".tooltip"));
    // }

    // public item.starrailexpress.binding_tool.tooltip
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide)
            return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // 反应堆绑定：好人（非创造）也可使用，用于关闭破坏任务的反应堆
        BlockEntity clicked = world.getBlockEntity(pos);
        if (clicked instanceof org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity reactor
                && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return handleReactorBind(serverLevel, reactor, pos, player);
        }
        // 水阀绑定
        if (clicked instanceof org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity valve
                && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return handleWaterValveBind(serverLevel, valve, pos, player);
        }
        if (clicked instanceof org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity pile
                && world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            return handleDebrisPileBind(serverLevel, pile, pos, player);
        }

        if (!player.isCreative()) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (blockEntity instanceof ZiplineBlockEntity) {
            return handleZiplineBind(world, pos, player);
        }

        if (blockEntity instanceof CameraBlockEntity) {
            // 右键点击摄像头：保存摄像头位置
            lastCameraPos = pos;
            player.displayClientMessage(
                    Component.literal("已绑定摄像头: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                    true);
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            return InteractionResult.SUCCESS;
        } else if (blockEntity instanceof SecurityMonitorBlockEntity) {
            // 右键点击监控器：保存摄像头位置到监控器
            if (lastCameraPos != null) {
                SecurityMonitorBlockEntity monitorEntity = (SecurityMonitorBlockEntity) blockEntity;
                monitorEntity.addCameraPosition(CalcRelativePosition(pos, lastCameraPos));
                player.displayClientMessage(Component.literal("已将摄像头绑定到监控器").withStyle(ChatFormatting.AQUA), true);
                player.displayClientMessage(Component.literal("摄像头位置: X=" + lastCameraPos.getX() + ", Y="
                        + lastCameraPos.getY() + ", Z=" + lastCameraPos.getZ()).withStyle(ChatFormatting.GRAY), false);
                player.displayClientMessage(
                        Component.literal("监控器位置: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ())
                                .withStyle(ChatFormatting.GRAY),
                        false);
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                }
            } else {
                player.displayClientMessage(Component.literal("请先右键点击一个摄像头").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.SUCCESS;
        } else if (blockEntity instanceof RemoteRedstoneBlockEntity re) {
            if (player.isShiftKeyDown()) {
                lastCameraPos = null;
                re.setTargetBlockPos(null);
                player.displayClientMessage(
                        Component.translatable("message.item.starrailexpress.binding_tool.cleared")
                                .withStyle(ChatFormatting.GREEN),
                        true);
                return InteractionResult.SUCCESS;
            }
            if (lastCameraPos == null) {
                lastCameraPos = pos;
                player.displayClientMessage(
                        Component.translatable("message.item.starrailexpress.binding_tool.bind_pos_remote_redstone")
                                .withStyle(ChatFormatting.AQUA),
                        true);
            } else {
                var blockEntity2 = world.getBlockEntity(lastCameraPos);
                if (blockEntity2 instanceof RemoteRedstoneBlockEntity re2) {
                    re2.setTargetBlockPos(CalcRelativePosition(lastCameraPos,pos));
                    player.displayClientMessage(
                            Component
                                    .translatable("message.item.starrailexpress.binding_tool.bind_remote_redstone",
                                            lastCameraPos.toShortString(), pos.toShortString())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                }

                lastCameraPos = null;
            }
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(
                Component.translatable("message.item.starrailexpress.binding_tool.invalid")
                        .withStyle(ChatFormatting.RED),
                true);
        return InteractionResult.PASS;
    }
}
