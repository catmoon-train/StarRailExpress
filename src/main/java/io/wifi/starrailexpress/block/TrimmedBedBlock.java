package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block_entity.TrimmedBedBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class TrimmedBedBlock extends BedBlock {
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public TrimmedBedBlock(Properties settings) {
        super(DyeColor.WHITE, settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
    }

    @Nullable
    public static Direction getBedOrientation(BlockGetter world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.getBlock() instanceof TrimmedBedBlock ? blockState.getValue(FACING) : null;
    }

    private static Direction getNeighbourDirection(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }

    private static boolean isBunkBed(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos.below()).getBlock() instanceof TrimmedBedBlock;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> type, CollisionGetter world, BlockPos pos, Direction bedDirection, float spawnAngle) {
        Direction direction = bedDirection.getClockWise();
        Direction direction2 = direction.isFacingAngle(spawnAngle) ? direction.getOpposite() : direction;
        if (TrimmedBedBlock.isBunkBed(world, pos)) {
            return TrimmedBedBlock.findBunkBedStandUpPosition(type, world, pos, bedDirection, direction2);
        }
        int[][] is = TrimmedBedBlock.bedStandUpOffsets(bedDirection, direction2);
        Optional<Vec3> optional = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, is, true);
        if (optional.isPresent()) {
            return optional;
        }
        return TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, is, false);
    }

    private static Optional<Vec3> findBunkBedStandUpPosition(EntityType<?> type, CollisionGetter world, BlockPos pos, Direction bedDirection, Direction respawnDirection) {
        int[][] is = TrimmedBedBlock.bedSurroundStandUpOffsets(bedDirection, respawnDirection);
        Optional<Vec3> optional = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, is, true);
        if (optional.isPresent()) {
            return optional;
        }
        BlockPos blockPos = pos.below();
        Optional<Vec3> optional2 = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, blockPos, is, true);
        if (optional2.isPresent()) {
            return optional2;
        }
        int[][] js = TrimmedBedBlock.bedAboveStandUpOffsets(bedDirection);
        Optional<Vec3> optional3 = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, js, true);
        if (optional3.isPresent()) {
            return optional3;
        }
        Optional<Vec3> optional4 = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, is, false);
        if (optional4.isPresent()) {
            return optional4;
        }
        Optional<Vec3> optional5 = TrimmedBedBlock.findStandUpPositionAtOffset(type, world, blockPos, is, false);
        if (optional5.isPresent()) {
            return optional5;
        }
        return TrimmedBedBlock.findStandUpPositionAtOffset(type, world, pos, js, false);
    }

    private static Optional<Vec3> findStandUpPositionAtOffset(EntityType<?> type, CollisionGetter world, BlockPos pos, int[][] possibleOffsets, boolean ignoreInvalidPos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int[] is : possibleOffsets) {
            mutable.set(pos.getX() + is[0], pos.getY(), pos.getZ() + is[1]);
            Vec3 vec3d = DismountHelper.findSafeDismountLocation(type, world, mutable, ignoreInvalidPos);
            if (vec3d == null) continue;
            return Optional.of(vec3d);
        }
        return Optional.empty();
    }

    private static int[][] bedStandUpOffsets(Direction bedDirection, Direction respawnDirection) {
        return ArrayUtils.addAll(TrimmedBedBlock.bedSurroundStandUpOffsets(bedDirection, respawnDirection), TrimmedBedBlock.bedAboveStandUpOffsets(bedDirection));
    }

    private static int[][] bedSurroundStandUpOffsets(Direction bedDirection, Direction respawnDirection) {
        return new int[][]{{respawnDirection.getStepX(), respawnDirection.getStepZ()}, {respawnDirection.getStepX() - bedDirection.getStepX(), respawnDirection.getStepZ() - bedDirection.getStepZ()}, {respawnDirection.getStepX() - bedDirection.getStepX() * 2, respawnDirection.getStepZ() - bedDirection.getStepZ() * 2}, {-bedDirection.getStepX() * 2, -bedDirection.getStepZ() * 2}, {-respawnDirection.getStepX() - bedDirection.getStepX() * 2, -respawnDirection.getStepZ() - bedDirection.getStepZ() * 2}, {-respawnDirection.getStepX() - bedDirection.getStepX(), -respawnDirection.getStepZ() - bedDirection.getStepZ()}, {-respawnDirection.getStepX(), -respawnDirection.getStepZ()}, {-respawnDirection.getStepX() + bedDirection.getStepX(), -respawnDirection.getStepZ() + bedDirection.getStepZ()}, {bedDirection.getStepX(), bedDirection.getStepZ()}, {respawnDirection.getStepX() + bedDirection.getStepX(), respawnDirection.getStepZ() + bedDirection.getStepZ()}};
    }

    private static int[][] bedAboveStandUpOffsets(Direction bedDirection) {
        return new int[][]{{0, 0}, {-bedDirection.getStepX(), -bedDirection.getStepZ()}};
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.CONSUME;
        } else {
            if (!player.isCreative() && player.getItemInHand(InteractionHand.MAIN_HAND).is(TMMItems.SCORPION)) {
                TrimmedBedBlockEntity blockEntity = null;

                if (world.getBlockEntity(pos) instanceof TrimmedBedBlockEntity firstBlockEntity) {
                    if (world.getBlockState(pos).getValue(PART) == BedPart.HEAD)
                        blockEntity = firstBlockEntity;
                    else {
                        BlockPos headPos = pos.relative(world.getBlockState(pos).getValue(FACING));
                        if (world.getBlockEntity(headPos) instanceof TrimmedBedBlockEntity foundBlockEntity)
                            blockEntity = foundBlockEntity;
                    }
                }

                if (blockEntity != null) {
                    if (!blockEntity.hasScorpion()) {
                        blockEntity.setHasScorpion(true, player.getUUID());
                        player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.SCORPION));
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }

            if (state.getValue(PART) != BedPart.HEAD) {
                pos = pos.relative(state.getValue(FACING));
                state = world.getBlockState(pos);
                if (!state.is(this)) {
                    return InteractionResult.CONSUME;
                }
            }

            if (state.getValue(OCCUPIED)) {
                if (!this.wakePlayers(world, pos)) {
                    player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
                }

                return InteractionResult.SUCCESS;
            } else {
                player.startSleepInBed(pos).ifLeft(reason -> {
                    if (reason.getMessage() != null) {
                        player.displayClientMessage(reason.getMessage(), true);
                    }
                });
                return InteractionResult.SUCCESS;
            }
        }
    }

    private boolean wakePlayers(Level world, BlockPos pos) {
        List<Player> list = world.getEntitiesOfClass(Player.class, new AABB(pos), LivingEntity::isSleeping);
        if (list.isEmpty()) {
            return false;
        } else {
            (list.get(0)).stopSleeping();
            return true;
        }
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClientSide || !type.equals(TMMBlockEntities.TRIMMED_BED)) {
            return null;
        }
        return TrimmedBedBlockEntity::clientTick;
    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(world, state, pos, entity, fallDistance * 0.5f);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(world, entity);
        } else {
            this.bounceUp(entity);
        }
    }

    private void bounceUp(Entity entity) {
        Vec3 vec3d = entity.getDeltaMovement();
        if (vec3d.y < 0.0) {
            double d = entity instanceof LivingEntity ? 1.0 : 0.8;
            entity.setDeltaMovement(vec3d.x, -vec3d.y * (double) 0.66f * d, vec3d.z);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == TrimmedBedBlock.getNeighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            if (neighborState.is(this) && neighborState.getValue(PART) != state.getValue(PART)) {
                return state.setValue(OCCUPIED, neighborState.getValue(OCCUPIED));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide && player.isCreative()) {
            BedPart bedPart = state.getValue(PART);
            if (bedPart == BedPart.FOOT) {
                BlockPos blockPos = pos.relative(getNeighbourDirection(bedPart, state.getValue(FACING)));
                BlockState blockState = world.getBlockState(blockPos);
                if (blockState.is(this) && blockState.getValue(PART) == BedPart.HEAD) {
                    world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    world.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, Block.getId(blockState));
                }
            }
        }

        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction direction = ctx.getHorizontalDirection();
        BlockPos blockPos = ctx.getClickedPos();
        BlockPos blockPos2 = blockPos.relative(direction);
        Level world = ctx.getLevel();
        if (world.getBlockState(blockPos2).canBeReplaced(ctx) && world.getWorldBorder().isWithinBounds(blockPos2)) {
            return this.defaultBlockState().setValue(FACING, direction);
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        if (!world.isClientSide) {
            BlockPos blockPos = pos.relative(state.getValue(FACING));
            world.setBlock(blockPos, state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            world.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(world, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrimmedBedBlockEntity(TMMBlockEntities.TRIMMED_BED, pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
