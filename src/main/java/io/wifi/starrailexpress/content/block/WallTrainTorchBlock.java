package io.wifi.starrailexpress.content.block;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallTrainTorchBlock extends TrainTorchBlock {
    public SimpleParticleType flameParticle;
    public static final MapCodec<WallTrainTorchBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
            .group(PARTICLE_OPTIONS_FIELD.forGetter((wallTorchBlock) -> wallTorchBlock.flameParticle),
                    propertiesCodec())
            .apply(instance, WallTrainTorchBlock::new));

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    protected static final float AABB_OFFSET = 2.5F;
    private static final Map<Direction, VoxelShape> AABBS = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH,
            Block.box((double) 5.5F, (double) 3.0F, (double) 11.0F, (double) 10.5F, (double) 13.0F, (double) 16.0F),
            Direction.SOUTH,
            Block.box((double) 5.5F, (double) 3.0F, (double) 0.0F, (double) 10.5F, (double) 13.0F, (double) 5.0F),
            Direction.WEST,
            Block.box((double) 11.0F, (double) 3.0F, (double) 5.5F, (double) 16.0F, (double) 13.0F, (double) 10.5F),
            Direction.EAST,
            Block.box((double) 0.0F, (double) 3.0F, (double) 5.5F, (double) 5.0F, (double) 13.0F, (double) 10.5F)));

    public MapCodec<? extends WallTrainTorchBlock> codec() {
        return CODEC;
    }

    public WallTrainTorchBlock(SimpleParticleType simpleParticleType, Properties properties) {
        super(simpleParticleType, properties);
        properties.lightLevel(WallTrainTorchBlock::lightBlockSupplier);
        this.flameParticle = simpleParticleType;
        this.registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true).setValue(ACTIVE,
                        true));
    }


    public String getDescriptionId() {
        return this.asItem().getDescriptionId();
    }

    @Override
    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos,
            CollisionContext collisionContext) {
        return getShape(blockState);
    }

    public static VoxelShape getShape(BlockState blockState) {
        return (VoxelShape) AABBS.get(blockState.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
        return true;
    }

    public static boolean canSurvive(LevelReader levelReader, BlockPos blockPos, Direction direction) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
        BlockState blockState = this.defaultBlockState();
        LevelReader levelReader = blockPlaceContext.getLevel();
        BlockPos blockPos = blockPlaceContext.getClickedPos();
        Direction[] directions = blockPlaceContext.getNearestLookingDirections();

        for (Direction direction : directions) {
            if (direction.getAxis().isHorizontal()) {
                Direction direction2 = direction.getOpposite();
                blockState = (BlockState) blockState.setValue(FACING, direction2);
                if (blockState.canSurvive(levelReader, blockPos)) {
                    return blockState;
                }
            }
        }

        return null;
    }

    @Override
    protected BlockState updateShape(BlockState blockState, Direction direction, BlockState blockState2,
            LevelAccessor levelAccessor, BlockPos blockPos, BlockPos blockPos2) {
        return direction.getOpposite() == blockState.getValue(FACING) && !blockState.canSurvive(levelAccessor, blockPos)
                ? Blocks.AIR.defaultBlockState()
                : blockState;
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        Direction direction = (Direction) blockState.getValue(FACING);
        double d = (double) blockPos.getX() + (double) 0.5F;
        double e = (double) blockPos.getY() + 0.7;
        double f = (double) blockPos.getZ() + (double) 0.5F;
        Direction direction2 = direction.getOpposite();
        level.addParticle(ParticleTypes.SMOKE, d + 0.27 * (double) direction2.getStepX(), e + 0.22,
                f + 0.27 * (double) direction2.getStepZ(), (double) 0.0F, (double) 0.0F, (double) 0.0F);
        level.addParticle(this.flameParticle, d + 0.27 * (double) direction2.getStepX(), e + 0.22,
                f + 0.27 * (double) direction2.getStepZ(), (double) 0.0F, (double) 0.0F, (double) 0.0F);
    }

    protected BlockState rotate(BlockState blockState, Rotation rotation) {
        return (BlockState) blockState.setValue(FACING, rotation.rotate((Direction) blockState.getValue(FACING)));
    }

    protected BlockState mirror(BlockState blockState, Mirror mirror) {
        return blockState.rotate(mirror.getRotation((Direction) blockState.getValue(FACING)));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[] { LIT, ACTIVE, FACING });
    }
}
