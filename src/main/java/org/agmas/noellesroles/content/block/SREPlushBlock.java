package org.agmas.noellesroles.content.block;

import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.init.SREFumoBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.doctor4t.ratatouille.block.PlushBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

public class SREPlushBlock extends PlushBlock {
   private static final MapCodec<SREPlushBlock> CODEC = simpleCodec(SREPlushBlock::new);
   public static final BooleanProperty WATERLOGGED;
   public static final EnumProperty<Direction> FACING;
   private static final VoxelShape SHAPE;

   public SREPlushBlock(BlockBehaviour.Properties settings) {
      super(settings);
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public static SoundEvent getSound(BlockState state) {
      SoundEvent ret = NRSounds.BAKA_BAKA;
      if (state.getBlock() == SREFumoBlocks.BAKA_PLUSH) {
         ret = NRSounds.BAKA_BAKA;
      }
      
      if (state.getBlock() == SREFumoBlocks.MILK_DRAGON_PLUSH) {
         ret = NRSounds.WO_SHI_NAI_LONG;
      }
      return ret;
   }

   @Override
   public RenderShape getRenderShape(BlockState state) {
      return super.getRenderShape(state);
   }

   @Override
   public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
      super.stepOn(level, pos, state, entity);
      if (!level.isClientSide && entity instanceof Player player) {
         // 落地时 fallDistance > 0，并且玩家已经着地
         if (player.fallDistance > 0.1F) {
            triggerPlush(state, level, pos, player);
         }
      }
   }

   @Override
   public void attack(BlockState state, Level world, BlockPos pos, Player player) {
      if (!world.isClientSide) {
         Vec3 mid = Vec3.atCenterOf(pos);
         float pitch = 1.2F + world.random.nextFloat() * 0.4F;
         BlockState note = world.getBlockState(pos.below());
         if (note.hasProperty(BlockStateProperties.NOTE)) {
            pitch = (float) Math.pow((double) 2.0F,
                  (double) ((Integer) note.getValue(BlockStateProperties.NOTE) - 12) / (double) 12.0F);
         }

         BlockEntity blockEntity = world.getBlockEntity(pos);
         if (blockEntity instanceof SREPlushBlockEntity) {
            SREPlushBlockEntity plushie = (SREPlushBlockEntity) blockEntity;
            plushie.squish(24);
         }
      }

   }

   @Override
   protected void spawnDestroyParticles(Level world, Player player, BlockPos pos, BlockState state) {
      BlockEntity var6 = world.getBlockEntity(pos);
      if (var6 instanceof SREPlushBlockEntity plushie) {
         plushie.squish(4);
      }

      super.spawnDestroyParticles(world, player, pos, state);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
         BlockHitResult hit) {
      if (!world.isClientSide) {
         triggerPlush(state, world, pos, player);
      }

      return InteractionResult.SUCCESS;
   }

   public void triggerPlush(BlockState state, Level world, BlockPos pos, Player player) {
      Vec3 mid = Vec3.atCenterOf(pos);
      float pitch = 0.8F + world.random.nextFloat() * 0.4F;
      BlockState note = world.getBlockState(pos.below());
      if (note.hasProperty(BlockStateProperties.NOTE)) {
         pitch = (float) Math.pow((double) 2.0F,
               (double) ((Integer) note.getValue(BlockStateProperties.NOTE) - 12) / (double) 12.0F);
      }

      world.playSound((Player) null, mid.x(), mid.y(), mid.z(), getSound(state), SoundSource.BLOCKS, 1.0F, 1.0F);
      BlockEntity var10 = world.getBlockEntity(pos);
      if (var10 instanceof SREPlushBlockEntity) {
         SREPlushBlockEntity plushie = (SREPlushBlockEntity) var10;
         plushie.squish(1);
      }
   }

   @Override

   public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   @Override
   public boolean useShapeForLightOcclusion(BlockState state) {
      return true;
   }

   @Override
   public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level world, BlockState state,
         BlockEntityType<T> type) {
      return createTickerHelper(type, SREFumoBlocks.PLUSH_BLOCK_ENTITY, SREPlushBlockEntity::tick);
   }

   @Override
   public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new SREPlushBlockEntity(pos, state);
   }

   @Override
   public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
      FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
      return (BlockState) ((BlockState) this.defaultBlockState().setValue(FACING,
            ctx.getHorizontalDirection().getOpposite())).setValue(WATERLOGGED, fluidState.is(Fluids.WATER));
   }

   @Override
   public BlockState rotate(BlockState state, Rotation rotation) {
      return (BlockState) state.setValue(FACING, rotation.rotate((Direction) state.getValue(FACING)));
   }

   @Override
   public BlockState mirror(BlockState state, Mirror mirror) {
      return state.rotate(mirror.getRotation((Direction) state.getValue(FACING)));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(new Property[] { FACING, WATERLOGGED });
   }

   @Override
   public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
         BlockPos pos, BlockPos neighborPos) {
      if ((Boolean) state.getValue(WATERLOGGED)) {
         world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
      }

      return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
   }

   @Override
   public FluidState getFluidState(BlockState state) {
      return (Boolean) state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
   }

   static {
      WATERLOGGED = BlockStateProperties.WATERLOGGED;
      FACING = BlockStateProperties.HORIZONTAL_FACING;
      SHAPE = box((double) 3.0F, (double) 0.0F, (double) 3.0F, (double) 13.0F, (double) 15.0F, (double) 13.0F);
   }
}
