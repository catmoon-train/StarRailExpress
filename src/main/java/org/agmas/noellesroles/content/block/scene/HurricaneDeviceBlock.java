package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import org.agmas.noellesroles.client.screen.HurricaneDeviceConfigScreen;
import org.agmas.noellesroles.content.block_entity.scene.HurricaneDeviceBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class HurricaneDeviceBlock extends BaseEntityBlock {
    public HurricaneDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide && player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HurricaneDeviceBlockEntity hbe) {
                Minecraft.getInstance().setScreen(new HurricaneDeviceConfigScreen(pos, hbe.getRadius(),
                        hbe.isPersistent(), hbe.getSpawnIntervalSeconds(), hbe.getDurationSeconds()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HurricaneDeviceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModSceneBlocks.HURRICANE_DEVICE_ENTITY,
                (lvl, pos, s, be) -> HurricaneDeviceBlockEntity.serverTick(lvl, pos, s, be));
    }
}
