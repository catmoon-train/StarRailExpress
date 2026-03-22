package io.wifi.starrailexpress.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FoodPlatterBlock extends BaseEntityBlock {
    public static final MapCodec<FoodPlatterBlock> CODEC = simpleCodec(FoodPlatterBlock::new);

    public FoodPlatterBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        BeveragePlateBlockEntity plate = new BeveragePlateBlockEntity(pos, state);
        plate.setDrink(false);
        return plate;
    }

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
        if (!(world.getBlockEntity(pos) instanceof BeveragePlateBlockEntity blockEntity))
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
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(TMMItems.DEFENSE_VIAL));
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

            boolean hasPlatterItem = false;
            for (ItemStack platterItem : platter) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack invItem = player.getInventory().getItem(i);
                    if (invItem.getItem() == platterItem.getItem()) {
                        hasPlatterItem = true;
                        break;
                    }
                }
                if (hasPlatterItem)
                    break;
            }

            if (!hasPlatterItem) {
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
        if (!world.isClientSide || !type.equals(TMMBlockEntities.BEVERAGE_PLATE))
            return null;
        return BeveragePlateBlockEntity::clientTick;
    }
}
