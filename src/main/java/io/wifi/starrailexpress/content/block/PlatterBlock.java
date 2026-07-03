package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public abstract class PlatterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static <B extends Block> MapCodec<B> createSimpleCodec(Function<Properties, B> function) {
        return simpleCodec(function);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(new Property[] { FACING });
    }

    public final void registerDefaultStateMirrored(BlockState blockState) {
        this.registerDefaultState(blockState);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public PlatterBlock(Properties settings) {
        super(settings);

        this.registerDefaultStateMirrored(
                (BlockState) ((BlockState) this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected abstract MapCodec<? extends BaseEntityBlock> codec();

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return newPlateBlockEntity(pos, state);
    };

    public abstract @Nullable BlockEntity newPlateBlockEntity(BlockPos pos, BlockState state);

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return this.getShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return this.getShape(state);
    }

    protected VoxelShape getShape(BlockState state) {
        return box(0, 0, 0, 16, 2, 16);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, @NotNull Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof PlateTrayBlockEntity blockEntity))
            return InteractionResult.PASS;
        if (player.isCreative()) {
            ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                blockEntity.addItem(heldItem);
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                            BuiltInRegistries.ITEM.getKey(heldItem.getItem()));
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(TMMItems.DEFENSE_VIAL)
                && blockEntity.getArmorer() == null) {
            blockEntity.setArmorer(player.getStringUUID());
            player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
            player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1f);
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                        BuiltInRegistries.ITEM.getKey(TMMItems.DEFENSE_VIAL));
            }
            return InteractionResult.SUCCESS;
        }
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(TMMItems.POISON_VIAL)
                && blockEntity.getPoisoner() == null) {
            blockEntity.setPoisoner(player.getStringUUID());
            player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
            player.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5f, 1f);
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.POISON_VIAL));
            }
            return InteractionResult.SUCCESS;
        }
        if (player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            List<ItemStack> platter = blockEntity.getStoredItems();
            if (platter.isEmpty()) {
                return InteractionResult.SUCCESS;
            }

            // 统计玩家身上已持有的“本盘食材”数量（含背包槽位与收纳袋内）。
            Set<Item> platterTypes = new HashSet<>();
            for (ItemStack platterItem : platter) {
                platterTypes.add(platterItem.getItem());
            }
            int heldFromPlatter = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invItem = player.getInventory().getItem(i);
                if (invItem.getItem() instanceof BundleItem) {
                    BundleContents bundleContents = invItem.get(DataComponents.BUNDLE_CONTENTS);
                    if (bundleContents != null) {
                        for (ItemStack itemStack : bundleContents.items()) {
                            if (platterTypes.contains(itemStack.getItem())) {
                                heldFromPlatter += itemStack.getCount();
                            }
                        }
                    }
                }
                if (platterTypes.contains(invItem.getItem())) {
                    heldFromPlatter += invItem.getCount();
                }
            }

            // 默认每个盘子每人只能拿1份；“饥渴”修饰符放宽到2份。
            int takeLimit = 1;
            WorldModifierComponent modifierComponent = WorldModifierComponent.KEY.get(world);
            if (modifierComponent != null && modifierComponent.isModifier(player, NRModifiers.HUNGRY)) {
                takeLimit = 2;
            }

            if (heldFromPlatter < takeLimit) {
                ItemStack randomItem = platter.get(world.random.nextInt(platter.size())).copy();
                randomItem.setCount(1);
                randomItem.set(DataComponents.MAX_STACK_SIZE, 1);
                String poisoner = blockEntity.getPoisoner();
                String armorer = blockEntity.getArmorer();

                if (poisoner != null) {
                    randomItem.set(SREDataComponentTypes.POISONER, poisoner);
                    blockEntity.setPoisoner(null);
                }
                if (armorer != null) {
                    randomItem.set(SREDataComponentTypes.ARMORER, armorer);
                    blockEntity.setArmorer(null);
                }
                player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
                player.setItemInHand(InteractionHand.MAIN_HAND, randomItem);
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level world, BlockState state,
            BlockEntityType<T> type) {
        if (!world.isClientSide)
            return null;
        return PlateTrayBlockEntity::clientTick;
    }
}
