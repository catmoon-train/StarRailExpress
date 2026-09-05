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
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
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
import org.agmas.noellesroles.scene.LoopingMirrorLoop;
import org.agmas.noellesroles.scene.LoopingMirrorManager;
import org.agmas.noellesroles.scene.LoopingMirrorPlane;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环镜子工具：先右键选中一块循环镜子作为宿主，再框选两个平面（各点两个对角），绑定到该镜子上。
 * 潜行右键已配置的镜子可清除；潜行右键空气清空当前勾选。
 */
public class LoopingMirrorToolItem extends Item {
    private static final String HOST_KEY = "LoopingMirrorHost";
    private static final String CORNERS_KEY = "LoopingMirrorCorners";
    private static final String FACES_KEY = "LoopingMirrorFaces";

    public LoopingMirrorToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
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
        if (player.isShiftKeyDown() && isMirror && LoopingMirrorManager.removeContaining(serverLevel, pos)) {
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.cleared")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }

        BlockPos host = readHost(stack);
        List<BlockPos> corners = readCorners(stack);
        List<Direction> faces = readFaces(stack);

        if (isMirror && (host == null || corners.isEmpty())) {
            writeHost(stack, pos);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.selected_host",
                            pos.getX(), pos.getY(), pos.getZ())
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        if (host == null) {
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.need_host")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        for (BlockPos selected : corners) {
            if (selected.equals(pos)) {
                player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.already_selected")
                        .withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.SUCCESS;
            }
        }

        corners.add(pos.immutable());
        faces.add(face);
        if (corners.size() == 2 || corners.size() == 4) {
            LoopingMirrorPlane probe = LoopingMirrorPlane.fromCorners(
                    corners.get(corners.size() - 2), corners.get(corners.size() - 1),
                    faces.get(faces.size() - 1));
            if (!probe.isValid()) {
                corners.remove(corners.size() - 1);
                faces.remove(faces.size() - 1);
                writeSelection(stack, host, corners, faces);
                player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.plane_too_large")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
        }

        if (corners.size() >= 4) {
            if (!(level.getBlockEntity(host) instanceof LoopingMirrorBlockEntity)
                    || !(level.getBlockState(host).getBlock() instanceof LoopingMirrorBlock)) {
                clearSelection(stack);
                player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.no_host_block")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            LoopingMirrorPlane planeA = LoopingMirrorPlane.fromCorners(corners.get(0), corners.get(1), faces.get(1));
            LoopingMirrorPlane planeB = LoopingMirrorPlane.fromCorners(corners.get(2), corners.get(3), faces.get(3));
            LoopingMirrorLoop loop = LoopingMirrorLoop.create(host, planeA, planeB);
            if (loop == null) {
                corners.remove(3);
                faces.remove(3);
                writeSelection(stack, host, corners, faces);
                player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.invalid_plane")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            LoopingMirrorManager.addAndBind(serverLevel, loop);
            clearSelection(stack);
            player.displayClientMessage(Component.translatable("message.noellesroles.looping_mirror.configured",
                            loop.planeA().outward().getSerializedName(),
                            loop.planeA().uSize(), loop.planeA().vSize(),
                            loop.planeB().outward().getSerializedName(),
                            loop.planeB().uSize(), loop.planeB().vSize())
                    .withStyle(ChatFormatting.GREEN), false);
            return InteractionResult.SUCCESS;
        }

        writeSelection(stack, host, corners, faces);
        String key = corners.size() == 1
                ? "message.noellesroles.looping_mirror.selected_plane_a1"
                : corners.size() == 2
                ? "message.noellesroles.looping_mirror.selected_plane_a2"
                : "message.noellesroles.looping_mirror.selected_plane_b1";
        player.displayClientMessage(Component.translatable(key, pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.AQUA), true);
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
        int n = readCorners(stack).size();
        tooltip.add(Component.translatable(getDescriptionId() + ".progress",
                        host == null ? "-" : (host.getX() + "," + host.getY() + "," + host.getZ()), n)
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    public static @Nullable BlockPos readHost(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return NbtUtils.readBlockPos(tag, HOST_KEY).orElse(null);
    }

    public static List<BlockPos> readCorners(ItemStack stack) {
        List<BlockPos> result = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = tag.getList(CORNERS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            NbtUtils.readBlockPos(list.getCompound(i), "Pos").ifPresent(result::add);
        }
        return result;
    }

    private static List<Direction> readFaces(ItemStack stack) {
        List<Direction> result = new ArrayList<>();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = tag.getList(FACES_KEY, Tag.TAG_STRING);
        for (Tag element : list) {
            Direction face = Direction.byName(element.getAsString());
            result.add(face == null ? Direction.NORTH : face);
        }
        return result;
    }

    private static void writeHost(ItemStack stack, BlockPos host) {
        writeSelection(stack, host, List.of(), List.of());
    }

    private static void writeSelection(ItemStack stack, BlockPos host, List<BlockPos> corners, List<Direction> faces) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put(HOST_KEY, NbtUtils.writeBlockPos(host));
        ListTag list = new ListTag();
        for (BlockPos pos : corners) {
            CompoundTag posTag = new CompoundTag();
            posTag.put("Pos", NbtUtils.writeBlockPos(pos));
            list.add(posTag);
        }
        tag.put(CORNERS_KEY, list);
        ListTag faceList = new ListTag();
        for (Direction face : faces) {
            faceList.add(net.minecraft.nbt.StringTag.valueOf(face.getSerializedName()));
        }
        tag.put(FACES_KEY, faceList);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(HOST_KEY);
        tag.remove(CORNERS_KEY);
        tag.remove(FACES_KEY);
        tag.remove("LoopingMirrorSelections");
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
