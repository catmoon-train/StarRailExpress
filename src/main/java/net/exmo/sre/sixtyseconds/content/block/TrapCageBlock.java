package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.content.block_entity.TrapCageBlockEntity;
import net.exmo.sre.sixtyseconds.logic.TrapCageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 捕捉笼：18格容器，可放入诱饵。每天早上消耗一个诱饵，有概率产出动物物品。
 * 仅可放置在白色混凝土上方（由 SixtySecondsStationBlock 的放置逻辑统一处理）。
 */
public class TrapCageBlock extends BaseEntityBlock {

    public TrapCageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TrapCageBlock::new);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrapCageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof TrapCageBlockEntity be) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (syncId, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x2, syncId, inventory, be, 2),
                        Component.translatable(getDescriptionId())));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            TrapCageSystem.register(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                TrapCageSystem.unregister(serverLevel, pos);
            }
            if (level.getBlockEntity(pos) instanceof TrapCageBlockEntity be) {
                Containers.dropContents(level, pos, be.contents());
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
