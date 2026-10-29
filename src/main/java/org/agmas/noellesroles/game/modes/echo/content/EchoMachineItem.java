package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/** 一次使用放下整台机器；所有格子预检通过后才开始写入，不接受物品篡改 PART/FACING。 */
public final class EchoMachineItem extends BlockItem {
    public EchoMachineItem(EchoMachineBlock block, Properties properties) { super(block, properties); }

    private record Placement(BlockPos pos, BlockState before, BlockState after) { }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        EchoMachineBlock block = (EchoMachineBlock) getBlock();
        if (!block.isEnabled(level.enabledFeatures())) return InteractionResult.FAIL;
        BlockState rootState = block.getStateForPlacement(context);
        BlockPos root = context.getClickedPos();
        List<Placement> placements = new ArrayList<>();
        // 先只检查范围与加载情况，避免碰撞查询触碰尚未加载的结构格。
        for (EchoMachineStructure.Cell cell : EchoMachineStructure.cells(block.kind())) {
            BlockPos pos = root.offset(EchoMachineStructure.offset(cell.part(), rootState.getValue(EchoMachineBlock.FACING)));
            if (!level.isInWorldBounds(pos) || level.isOutsideBuildHeight(pos)
                    || !level.getWorldBorder().isWithinBounds(pos)) return fail(context, "bounds");
            if (!level.hasChunkAt(pos)) return fail(context, "unloaded");
        }
        for (EchoMachineStructure.Cell cell : EchoMachineStructure.cells(block.kind())) {
            BlockPos pos = root.offset(EchoMachineStructure.offset(cell.part(), rootState.getValue(EchoMachineBlock.FACING)));
            BlockState before = level.getBlockState(pos);
            BlockState after = rootState.setValue(EchoMachineBlock.PART, cell.part());
            BlockPlaceContext local = BlockPlaceContext.at(context, pos, context.getClickedFace());
            if (before.hasBlockEntity() || level.getBlockEntity(pos) != null || !before.canBeReplaced(local)) {
                return fail(context, "occupied");
            }
            if (player != null && (!level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
                return fail(context, "permission");
            }
            if ((player != null && player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos)))
                    || !level.isUnobstructed(after, pos, CollisionContext.empty())) return fail(context, "collision");
            placements.add(new Placement(pos, before, after));
        }
        // 客户端只预测成功，不构造临时半台机器；服务器是唯一的整台写入方。
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean[] success = { true };
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        EchoMachineStructure.mutate(() -> {
            int written = 0;
            try {
                for (Placement placement : placements) {
                    if (level.getBlockState(placement.pos()) != placement.before()) {
                        success[0] = false;
                        break;
                    }
                    // 将本格纳入回滚，包含 setBlock 在回调中修改后返回 false 的情况。
                    written++;
                    if (!level.setBlock(placement.pos(), placement.after(), flags)) {
                        success[0] = false;
                        break;
                    }
                }
                if (success[0] && !EchoMachineStructure.complete(level, root)) success[0] = false;
            } catch (RuntimeException exception) {
                success[0] = false;
                throw exception;
            } finally {
                if (!success[0]) {
                    for (int i = written - 1; i >= 0; i--) {
                        Placement placement = placements.get(i);
                        level.setBlock(placement.pos(), placement.before(), flags);
                    }
                }
            }
        });
        if (!success[0]) return fail(context, "changed");
        EchoMachineStructure.notifyNeighbors(level, placements.stream().map(Placement::pos).toList(), block);
        ItemStack stack = context.getItemInHand();
        block.setPlacedBy(level, root, rootState, player, stack);
        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, root, stack);
        SoundType sound = rootState.getSoundType();
        level.playSound(null, root, getPlaceSound(rootState), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, root, GameEvent.Context.of(player, rootState));
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult fail(BlockPlaceContext context, String reason) {
        if (!context.getLevel().isClientSide && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable("echo_multiblock.place." + reason), true);
        }
        return InteractionResult.FAIL;
    }
}
