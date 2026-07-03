package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
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
import org.agmas.noellesroles.content.block_entity.scene.HurricaneDeviceBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class HurricaneDeviceBlock extends BaseEntityBlock {

    /** 客户端回调：打开飓风装置配置屏幕。由 NoellesrolesClient 在客户端初始化时设置。 */
    public static Consumer<BlockPos> openHurricaneDeviceConfigCallback;

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
        if (level.isClientSide && player.isCreative() && openHurricaneDeviceConfigCallback != null) {
            openHurricaneDeviceConfigCallback.accept(pos);
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
