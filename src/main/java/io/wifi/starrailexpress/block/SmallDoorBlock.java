package io.wifi.starrailexpress.block;

import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.event.AllowPlayerOpenLockedDoor;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallDoorBlock extends DoorPartBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape X_SHAPE = Block.box(7, 0, 0, 9, 16, 16);
    protected static final VoxelShape Z_SHAPE = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape[] SHAPES = createShapes();
    private final Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier;

    public SmallDoorBlock(Supplier<BlockEntityType<SmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
        this.typeSupplier = typeSupplier;
    }

    private static VoxelShape[] createShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        VoxelShape lowerXShape = Block.box(7, 0, 0, 9, 32, 16);
        VoxelShape lowerZShape = Block.box(0, 0, 7, 16, 32, 9);
        VoxelShape upperXShape = Block.box(7, -16, 0, 9, 16, 16);
        VoxelShape upperZShape = Block.box(0, -16, 7, 16, 16, 9);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int id = direction.get2DDataValue();
            boolean xAxis = direction.getAxis() == Direction.Axis.X;
            shapes[id] = xAxis ? lowerXShape : lowerZShape;
            shapes[id + 4] = xAxis ? upperXShape : upperZShape;
            Vector3f offset = direction.getClockWise().step().mul(7);
            AABB box = new AABB(7, 0, 7, 9, 32, 9).move(offset);
            shapes[id + 8] = Block.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            shapes[id + 12] = Block.box(box.minX, box.minY - 16, box.minZ, box.maxX, box.maxY - 16, box.maxZ);
        }
        return shapes;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        world.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == half.getDirectionToOther() &&
                (!neighborState.is(this)
                        || neighborState.getValue(FACING) != state.getValue(FACING)
                        || neighborState.getValue(HALF) != half.getOtherHalf())) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState placementState = super.getStateForPlacement(ctx);
        if (placementState == null) {
            return null;
        }
        BlockPos pos = ctx.getClickedPos();
        Level world = ctx.getLevel();
        return pos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(pos.above()).canBeReplaced(ctx) ? placementState : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (context.equals(CollisionContext.empty())) {
            return this.getShape(state);
        }
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean open = state.getValue(OPEN);
        return SHAPES[state.getValue(FACING).get2DDataValue() + (lower ? 0 : 4) + (open ? 8 : 0)];
    }

    @Override
    protected VoxelShape getShape(BlockState state) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? this.typeSupplier.get().create(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getTicker(world, state, type) : null;
    }

    @Override
    protected BlockEntityType<? extends DoorBlockEntity> getBlockEntityType() {
        return this.typeSupplier.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (world.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity entity) {
            if (entity.isBlasted()) {
                return InteractionResult.PASS;
            }

            if (player.isCreative() || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player)) {
                return open(state, world, entity, lowerPos);
            } else {
                boolean requiresKey = !entity.getKeyName().isEmpty();
                boolean hasLockpick = player.getMainHandItem().is(TMMItems.LOCKPICK);
                boolean jammed = entity.isJammed();

                if (entity.isOpen()) {
                    return open(state, world, entity, lowerPos);
                } else if (requiresKey && !jammed) {
                    if (player.getMainHandItem().is(TMMItems.CROWBAR)) return InteractionResult.FAIL;
                    if (player.getMainHandItem().is(TMMItems.KEY) || hasLockpick) {
                        ItemLore lore = player.getMainHandItem().get(DataComponents.LORE);
                        boolean isRightKey = lore != null && !lore.lines().isEmpty() && lore.lines().getFirst().getString().equals(entity.getKeyName());
                        if (isRightKey || hasLockpick) {
                            if (isRightKey)
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f, TMMSounds.ITEM_KEY_DOOR, SoundSource.BLOCKS, 1f, 1f);
                            if (hasLockpick)
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f, TMMSounds.ITEM_LOCKPICK_DOOR, SoundSource.BLOCKS, 1f, 1f);
                            return open(state, world, entity, lowerPos);
                        } else {
                            if (!world.isClientSide) {
                                world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                                player.displayClientMessage(Component.translatable("tip.door.requires_different_key"), true);
                            }
                            return InteractionResult.FAIL;
                        }
                    }

                    if (!world.isClientSide) {
                        world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                        player.displayClientMessage(Component.translatable("tip.door.requires_key"), true);
                    }
                    return InteractionResult.FAIL;
                } else {
                    if (jammed) {
                        if (!world.isClientSide) {
                            world.playSound(null, lowerPos.getX() + .5f, lowerPos.getY() + 1, lowerPos.getZ() + .5f, TMMSounds.BLOCK_DOOR_LOCKED, SoundSource.BLOCKS, 1f, 1f);
                            player.displayClientMessage(Component.translatable("tip.door.jammed"), true);
                        }
                    } else {
                        // open the door freely
                        return open(state, world, entity, lowerPos);
                    }
                }
            }
        }

        return InteractionResult.PASS;
    }

    static @NotNull InteractionResult open(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos) {
        if (world.isClientSide) return InteractionResult.SUCCESS;
        toggleDoor(state, world, entity, lowerPos);
        return InteractionResult.CONSUME;
    }

    public static void toggleDoor(BlockState state, Level world, SmallDoorBlockEntity entity, BlockPos lowerPos) {
        entity.toggle(false);
        Direction facing = state.getValue(FACING);
        BlockPos neighborPos = lowerPos.relative(facing.getCounterClockWise());
        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.is(state.getBlock())
                && neighborState.getValue(FACING).getOpposite() == facing
                && world.getBlockEntity(neighborPos) instanceof SmallDoorBlockEntity neighborEntity) {
            neighborEntity.toggle(true);
        }
    }


}
