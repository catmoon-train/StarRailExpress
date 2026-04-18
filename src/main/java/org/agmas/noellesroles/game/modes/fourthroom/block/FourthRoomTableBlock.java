package org.agmas.noellesroles.game.modes.fourthroom.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FourthRoomTableBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<TablePart> PART = EnumProperty.create("part", TablePart.class);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14.9, 16);

    public FourthRoomTableBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0F)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, TablePart.CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos corePos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (TablePart part : TablePart.values()) {
            BlockPos targetPos = corePos.offset(part.xOffset, 0, part.zOffset);
            boolean canReplace = context.getLevel().getBlockState(targetPos).canBeReplaced(context)
                    && context.getLevel().getWorldBorder().isWithinBounds(targetPos);
            if (!canReplace) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, TablePart.CENTER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            placeStructure(level, pos, state.getValue(FACING));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != TablePart.CENTER) {
            return null;
        }
        return new FourthRoomTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof FourthRoomTableBlockEntity table) {
                    table.clientTick();
                }
            };
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos corePos = getCore(state, pos);
        BlockEntity be = level.getBlockEntity(corePos);
        if (be instanceof FourthRoomTableBlockEntity table) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (table.linkedRoomId() >= 0 && FourthRoomGameManager.of((net.minecraft.server.level.ServerLevel) level).data().active) {
                if (player instanceof ServerPlayer serverPlayer) {
                    table.handleBattleInteraction(serverPlayer, resolveInteractionZone(state, pos, hitResult));
                }
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                table.onPlayerInteract(serverPlayer);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos corePos = getCore(state, pos);
        BlockEntity be = level.getBlockEntity(corePos);
        if (!(be instanceof FourthRoomTableBlockEntity table)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (table.linkedRoomId() >= 0 && FourthRoomGameManager.of((net.minecraft.server.level.ServerLevel) level).data().active) {
            if (player instanceof ServerPlayer serverPlayer) {
                table.handleBattleInteraction(serverPlayer, resolveInteractionZone(state, pos, hit));
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            table.onPlayerInteract(serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockPos corePos = getCore(state, pos);
        for (TablePart part : TablePart.values()) {
            if (!level.getBlockState(corePos.offset(part.xOffset, 0, part.zOffset)).is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            removeStructure(level, getCore(state, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    public static BlockPos getCore(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof FourthRoomTableBlock)) {
            return pos;
        }
        TablePart part = state.getValue(PART);
        return pos.offset(-part.xOffset, 0, -part.zOffset);
    }

    public static void placeStructure(LevelAccessor level, BlockPos corePos, Direction facing) {
        BlockState centerState = level.getBlockState(corePos);
        if (!(centerState.getBlock() instanceof FourthRoomTableBlock block)) {
            return;
        }
        for (TablePart part : TablePart.values()) {
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset),
                    block.defaultBlockState().setValue(FACING, facing).setValue(PART, part),
                    Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void removeStructure(Level level, BlockPos corePos) {
        for (TablePart part : TablePart.values()) {
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    public static InteractionZone resolveInteractionZone(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        if (!(state.getBlock() instanceof FourthRoomTableBlock)) {
            return InteractionZone.NONE;
        }
        BlockPos corePos = getCore(state, pos);
        Direction facing = state.getValue(FACING);
        Vec3 hitPos = hitResult.getLocation();
        InteractionZone zone = nearestZone(hitPos, corePos, facing);
        if (zone != InteractionZone.NONE) {
            return zone;
        }
        double x = hitPos.x - (corePos.getX() + 0.5D);
        double z = hitPos.z - (corePos.getZ() + 0.5D);
        if (Math.abs(x) <= 1.45D && Math.abs(z) <= 1.45D) {
            return InteractionZone.CENTER;
        }
        return InteractionZone.NONE;
    }

    private static InteractionZone nearestZone(Vec3 hitPos, BlockPos corePos, Direction facing) {
        InteractionZone closest = InteractionZone.NONE;
        double bestDistance = Double.MAX_VALUE;
        for (InteractionZone zone : InteractionZone.values()) {
            if (zone.anchor == null) {
                continue;
            }
            Vec3 anchorPos = zone.anchor.worldPos(corePos, facing);
            double distance = horizontalDistanceSquared(hitPos, anchorPos);
            if (distance <= zone.radius * zone.radius && distance < bestDistance) {
                closest = zone;
                bestDistance = distance;
            }
        }
        return closest;
    }

    private static double horizontalDistanceSquared(Vec3 first, Vec3 second) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }

    public enum InteractionZone {
        DRAW_PILE(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor.DRAW_PILE, 0.42D),
        DISCARD_PILE(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor.DISCARD_PILE, 0.42D),
        CENTER(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor.CENTER, 0.86D),
        SEAT_A(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor.SLOT_A, 0.48D),
        SEAT_B(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor.SLOT_B, 0.48D),
        NONE(null, 0.0D);

        private final org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor anchor;
        private final double radius;

        InteractionZone(org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents.TableAnchor anchor, double radius) {
            this.anchor = anchor;
            this.radius = radius;
        }

        public boolean isSeat() {
            return this == SEAT_A || this == SEAT_B;
        }
    }

    public enum TablePart implements StringRepresentable {
        NORTH_WEST(-1, -1),
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        WEST(-1, 0),
        CENTER(0, 0),
        EAST(1, 0),
        SOUTH_WEST(-1, 1),
        SOUTH(0, 1),
        SOUTH_EAST(1, 1);

        private final int xOffset;
        private final int zOffset;

        TablePart(int xOffset, int zOffset) {
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase();
        }
    }
}
