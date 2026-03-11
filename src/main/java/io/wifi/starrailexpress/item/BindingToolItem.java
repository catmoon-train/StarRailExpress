package io.wifi.starrailexpress.item;

import io.wifi.starrailexpress.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.SRE;
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

    public BindingToolItem(Properties settings) {
        super(settings);
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

        BlockEntity blockEntity = world.getBlockEntity(pos);

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
        } else {
            player.displayClientMessage(Component.literal("此工具只能用于摄像头和监控器").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.PASS;
        }
    }
}