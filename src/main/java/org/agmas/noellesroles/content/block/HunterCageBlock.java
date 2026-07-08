package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.HunterCageBlockEntity;
import net.exmo.sre.repair.arena.RepairArenaBuilder;
import net.exmo.sre.repair.util.RepairGameplayEffects;
import net.exmo.sre.repair.state.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof HunterCageBlockEntity cage) {
                cage.destroyCageStructure();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ModItems.HUNTER_CHAIN)) {
            // 处理猎人锁链对笼子的使用
            return handleHunterChainOnCage(stack, state, level, pos, player, hand, hitResult);
        }
        if (player instanceof ServerPlayer serverPlayer && RepairModeState.canUseHunterUtility(serverPlayer)) {
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

    private static ItemInteractionResult handleHunterChainOnCage(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || !(player instanceof ServerPlayer hunter) || !(level instanceof ServerLevel serverLevel)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!RepairModeState.canUseHunterUtility(hunter)) {
            return ItemInteractionResult.FAIL;
        }

        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        if (hunterComponent.carrying == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(serverLevel.getPlayerByUUID(hunterComponent.carrying) instanceof ServerPlayer prisoner)
                || !ModComponents.REPAIR_ROLES.get(prisoner).downed) {
            hunterComponent.carrying = null;
            hunterComponent.sync();
            return ItemInteractionResult.FAIL;
        }

        // 当前点击的就是笼子，直接使用现有的笼子
        if (level.getBlockEntity(pos) instanceof HunterCageBlockEntity cage) {
            if (!cage.addPrisoner(prisoner.getUUID(), hunter.getUUID())) {
                hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_full")
                        .withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.FAIL;
            }

            RepairModeState.startTrial(hunter, prisoner, pos);
            RepairGameplayEffects.burst(serverLevel, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 2);
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_started"), true);
            
            if (!hunter.getAbilities().instabuild) {
                stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.SUCCESS;
        }
        
        // 如果点击的不是笼子，但手持锁链，允许放置新笼子
        BlockPos placementPos = pos.relative(hitResult.getDirection());
        if (!level.getBlockState(placementPos).canBeReplaced()) {
            placementPos = findCagePos(serverLevel, pos);
        }
        if (placementPos == null) {
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.no_cage_space")
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.FAIL;
        }
        BlockState cageState = ModBlocks.HUNTER_CAGE.defaultBlockState();
        RepairArenaBuilder.trackGameplayPlacement(serverLevel, placementPos);
        level.setBlockAndUpdate(placementPos, cageState);
        if (level.getBlockEntity(placementPos) instanceof HunterCageBlockEntity createdCage) {
            if (!createdCage.addPrisoner(prisoner.getUUID(), hunter.getUUID())) {
                hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_full")
                        .withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.FAIL;
            }

            RepairModeState.startTrial(hunter, prisoner, placementPos);
            RepairGameplayEffects.burst(serverLevel, placementPos.getX() + 0.5D, placementPos.getY() + 1.0D, placementPos.getZ() + 0.5D, 2);
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.trial_started"), true);
            
            if (!hunter.getAbilities().instabuild) {
                stack.hurtAndBreak(1, hunter, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.SUCCESS;
        }
        
        return ItemInteractionResult.FAIL;
    }

    private static BlockPos findCagePos(ServerLevel level, BlockPos clickedPos) {
        for (BlockPos candidate : List.of(clickedPos, clickedPos.above(), clickedPos.north(), clickedPos.south(),
                clickedPos.east(), clickedPos.west())) {
            if (level.getBlockState(candidate).canBeReplaced() && level.getBlockState(candidate.above()).canBeReplaced()) {
                return candidate;
            }
        }
        for (BlockPos candidate : BlockPos.betweenClosed(clickedPos.offset(-1, 0, -1), clickedPos.offset(1, 1, 1))) {
            if (level.getBlockState(candidate).canBeReplaced() && level.getBlockState(candidate.above()).canBeReplaced()) {
                return candidate.immutable();
            }
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return rescue(level, pos, player, 20) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean rescue(Level level, BlockPos pos, Player player, int amount) {
        if (level.isClientSide()) {
            return false;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof HunterCageBlockEntity cage) {
            // 检查笼子里是否真的有人
            if (cage.getPrisoner().isEmpty()) {
                return false;
            }
            
            // 检查是否已经成功救出（进度100）
            if (cage.getRescueProgress() >= 100) {
                return false;
            }
            
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
            
            int oldProgress = cage.getRescueProgress();
            boolean released = cage.addRescueProgress(amount);
            int newProgress = cage.getRescueProgress();
            
            // 只有进度真正增加且未完成时才考虑给予金币奖励
            if (newProgress > oldProgress && !released) {
                // 在HunterCageBlockEntity中添加lastRescueRewardTick字段来跟踪时间
                long currentTick = level.getGameTime();
                long lastRewardTick = cage.getLastRescueRewardTick();
                
                // 每5秒（100 ticks）给一次3金币奖励
                if (currentTick - lastRewardTick >= 100) {
                    cage.setLastRescueRewardTick(currentTick);
                    RepairModeState.awardCoins(serverPlayer, 3, "repair_coin_source.rescuing");
                    serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", 3)
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                }
            }
            
            if (released) {
                // 成功救出时给55金币
                RepairModeState.awardCoins(serverPlayer, 55, "repair_coin_source.rescue");
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.rescued")
                        .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.coin_reward", 55)
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.rescuing", newProgress)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            }
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
