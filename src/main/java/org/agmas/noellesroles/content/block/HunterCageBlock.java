package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

public class HunterCageBlock extends BaseEntityBlock {
    private static final MapCodec<HunterCageBlock> CODEC = simpleCodec(HunterCageBlock::new);

    public HunterCageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ModItems.HUNTER_CHAIN) || player instanceof ServerPlayer serverPlayer
                && RepairModeState.canUseHunterUtility(serverPlayer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(ModItems.RESCUE_FLARE)) {
            boolean rescued = rescue(level, pos, player, 100);
            if (rescued && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return rescue(level, pos, player, 25)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return rescue(level, pos, player, 25) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean rescue(Level level, BlockPos pos, Player player, int amount) {
        if (level.isClientSide()) {
            return false;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof HunterCageBlockEntity cage) {
            boolean instantRescue = amount >= 100;
            String activeRole = ModComponents.REPAIR_ROLES.get(player).activeRole;
            if (!instantRescue && "medic".equals(activeRole)) {
                amount = Math.max(amount, 40);
            }
            if (!instantRescue && cage.getCaptor().flatMap(uuid -> level.getPlayerByUUID(uuid) instanceof net.minecraft.server.level.ServerPlayer captor
                    ? java.util.Optional.of(ModComponents.REPAIR_ROLES.get(captor).activeRole) : java.util.Optional.empty())
                    .filter("warden"::equals).isPresent()) {
                amount = Math.max(5, amount - 10);
            }
            boolean released = cage.addRescueProgress(amount);
            int reward = released ? 55 : 6;
            RepairModeState.awardCoins(serverPlayer, reward, released ? "repair_coin_source.rescue" : "repair_coin_source.rescuing");
            serverPlayer.displayClientMessage(Component.translatable(
                    released ? "message.noellesroles.repair.rescued" : "message.noellesroles.repair.rescuing",
                    Math.min(100, cage.getRescueProgress())), true);
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", reward)
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return released;
        }
        return false;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HunterCageBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof HunterCageBlockEntity cage) {
                HunterCageBlockEntity.tick(tickerLevel, pos, tickerState, cage);
            }
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
