package net.exmo.sre.repair.content.block;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.exmo.sre.repair.content.block_entity.RepairStationBlockEntity;
import net.exmo.sre.repair.content.item.HunterJammerItem;
import net.exmo.sre.repair.content.item.RepairBoostItem;
import net.exmo.sre.repair.network.OpenRepairStationScreenS2CPacket;
import org.jetbrains.annotations.Nullable;

public class RepairStationBlock extends BaseEntityBlock {
    private static final MapCodec<RepairStationBlock> CODEC = simpleCodec(RepairStationBlock::new);

    public RepairStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 修机加速道具和干扰器都在自己的 useOn 里处理修机台。这里必须让行，
        // 否则方块先把右键吃掉打开校准界面，物品的 useOn 根本没机会跑。
        if (stack.getItem() instanceof RepairBoostItem || stack.getItem() instanceof HunterJammerItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        openRepairGui(player, pos);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        openRepairGui(player, pos);
        return InteractionResult.SUCCESS;
    }

    private static void openRepairGui(Player player, BlockPos pos) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new OpenRepairStationScreenS2CPacket(pos));
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RepairStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof RepairStationBlockEntity station) {
                RepairStationBlockEntity.tick(tickerLevel, pos, tickerState, station);
            }
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
