package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.HotbarStorageBlockEntity;
import net.exmo.sre.repair.state.RepairModeState;
import net.exmo.sre.repair.state.RepairSearchState;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

public class HotbarStorageBlock extends BaseEntityBlock {
    private static final MapCodec<HotbarStorageBlock> CODEC = simpleCodec(HotbarStorageBlock::new);

    public HotbarStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HotbarStorageBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        open(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        open(level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && RepairModeState.isNonHunterRepairPlayer(serverPlayer)) {
            // 如果已经在搜索同一个箱子，不重复开始（防止进度重置）
            var comp = ModComponents.REPAIR_ROLES.get(serverPlayer);
            if (comp.searchTarget.present() && comp.searchTarget.toBlockPos().equals(pos)) {
                return;
            }
            if (!RoleUtils.isPlayerHasFreeSlot(serverPlayer)){
                player.displayClientMessage(Component.translatable("noellesroles.no_free_slot"),true);
                return;
            }
            RepairSearchState.begin(serverPlayer, pos);
            return;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof HotbarStorageBlockEntity storage) {
            serverPlayer.openMenu(storage);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof HotbarStorageBlockEntity storage) {
                Containers.dropContents(level, pos, storage);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
