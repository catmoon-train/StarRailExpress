/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import org.agmas.noellesroles.content.block.scene.LoopingMirrorBlock;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;
import org.agmas.noellesroles.scene.LoopingMirrorManager;
import org.agmas.noellesroles.scene.VerticalLoopingMirrorLoop;
import org.agmas.noellesroles.scene.VerticalLoopingMirrorManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 上下循环镜子工具：先右键循环镜子宿主，再勾选 A、B 两个对角，绑定一段竖向循环空间。
 */
public class VerticalLoopingMirrorToolItem extends Item {
    private static final String HOST_KEY = "VerticalLoopingMirrorHost";
    private static final String CORNER_A_KEY = "VerticalLoopingMirrorCornerA";

    public VerticalLoopingMirrorToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        boolean isMirror = level.getBlockState(pos).getBlock() instanceof LoopingMirrorBlock;
        if (player.isShiftKeyDown() && isMirror
                && (VerticalLoopingMirrorManager.removeContaining(serverLevel, pos)
                || LoopingMirrorManager.removeContaining(serverLevel, pos))) {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.cleared")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos host = readHost(stack);
        BlockPos cornerA = readCornerA(stack);

        if (isMirror && host == null) {
            writeSelection(stack, pos, null);
            player.displayClientMessage(Component.translatable("message.noellesroles.vertical_looping_mirror.selected_host",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        if (host == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.need_host")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        if (cornerA == null) {
            writeSelection(stack, host, pos.immutable());
            player.displayClientMessage(Component.translatable("message.noellesroles.vertical_looping_mirror.selected_a",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        if (cornerA.equals(pos)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.already_selected")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(host) instanceof LoopingMirrorBlockEntity)
                || !(level.getBlockState(host).getBlock() instanceof LoopingMirrorBlock)) {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.no_host_block")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        VerticalLoopingMirrorLoop loop = VerticalLoopingMirrorLoop.create(host, cornerA, pos);
        if (loop == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.vertical_looping_mirror.invalid_volume")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        VerticalLoopingMirrorManager.addAndBind(serverLevel, loop);
        clearSelection(stack);
        player.displayClientMessage(Component.translatable("message.noellesroles.vertical_looping_mirror.configured",
                        loop.sizeX(), loop.sizeY(), loop.sizeZ(), loop.copiesUp())
                .withStyle(ChatFormatting.GREEN), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.selection_cleared")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.3").withStyle(ChatFormatting.GRAY));
        BlockPos host = readHost(stack);
        BlockPos cornerA = readCornerA(stack);
        tooltip.add(Component.translatable(getDescriptionId() + ".progress",
                        host == null ? "-" : (host.getX() + "," + host.getY() + "," + host.getZ()),
                        cornerA == null ? "-" : (cornerA.getX() + "," + cornerA.getY() + "," + cornerA.getZ()))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    public static @Nullable BlockPos readHost(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return NbtUtils.readBlockPos(tag, HOST_KEY).orElse(null);
    }

    public static @Nullable BlockPos readCornerA(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return NbtUtils.readBlockPos(tag, CORNER_A_KEY).orElse(null);
    }

    private static void writeSelection(ItemStack stack, BlockPos host, @Nullable BlockPos cornerA) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(HOST_KEY, NbtUtils.writeBlockPos(host));
        if (cornerA == null) {
            tag.remove(CORNER_A_KEY);
        } else {
            tag.put(CORNER_A_KEY, NbtUtils.writeBlockPos(cornerA));
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(HOST_KEY);
        tag.remove(CORNER_A_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
