package io.wifi.events.day_night_fight.block;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.events.day_night_fight.entity.DNFTaskPointEntity;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class DNFTaskPointBlock extends Block {
    public enum TaskPointType {
        CLEANING,
        EXCHANGE
    }

    private final TaskPointType type;

    public DNFTaskPointBlock(TaskPointType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public TaskPointType getTaskPointType() {
        return type;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(DNFItems.TASK_TOOL)) {
            player.startUsingItem(hand);
            if (!world.isClientSide) {
                world.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.6f, 0.9f);
                ensureDisplayEntity(world, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (type == TaskPointType.EXCHANGE && stack.is(DNFItems.CLEANING_BYPRODUCT)) {
            if (world.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return ItemInteractionResult.FAIL;
            }
            return exchangeByproduct(serverPlayer, pos).consumesAction()
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, world, pos, oldState, movedByPiston);
        if (!world.isClientSide && world instanceof ServerLevel serverLevel && !state.is(oldState.getBlock())) {
            DNFTaskPointEntity.spawnForBlock(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            world.getEntitiesOfClass(DNFTaskPointEntity.class, new AABB(pos).inflate(1.5),
                    entity -> entity.isForBlock(pos)).forEach(Entity::discard);
        }
        super.onRemove(state, world, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !canUseDnfTaskPoint(serverPlayer)) {
            return InteractionResult.PASS;
        }
        ensureDisplayEntity(world, pos);
        if (type == TaskPointType.EXCHANGE) {
            return exchangeByproduct(serverPlayer, pos);
        }
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.task_point.need_tool")
                .withStyle(ChatFormatting.YELLOW), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.05;
        double z = pos.getZ() + 0.5;
        if (type == TaskPointType.EXCHANGE) {
            world.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0.02, 0);
        } else {
            world.addParticle(ParticleTypes.CLOUD, x + (random.nextDouble() - 0.5) * 0.4, y, z + (random.nextDouble() - 0.5) * 0.4,
                    0, 0.01, 0);
        }
    }

    public InteractionResult completeChargedTask(ServerPlayer player, BlockPos pos) {
        if (!canUseDnfTaskPoint(player)) {
            return InteractionResult.PASS;
        }
        ensureDisplayEntity(player.level(), pos);
        return switch (type) {
            case CLEANING -> completeCleaning(player, pos);
            case EXCHANGE -> exchangeByproduct(player, pos);
        };
    }

    private static boolean canUseDnfTaskPoint(ServerPlayer player) {
        return DNF.isDayNightFightMode(player.level()) && GameUtils.isPlayerAliveAndSurvival(player);
    }

    private InteractionResult completeCleaning(ServerPlayer player, BlockPos pos) {
        DNFPlayerComponent component = DNFPlayerComponent.KEY.get(player);
        if (!component.beginCleaningTask(player)) {
            return InteractionResult.FAIL;
        }
        component.finishCleaningTask(player, SREPlayerTaskComponent.Task.DNF_PRISON_DUST,
                "message.dnf.task.task_point_cleaning");
        player.level().playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 1.0f, 1.1f);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    10, 0.35, 0.25, 0.35, 0.02);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult exchangeByproduct(ServerPlayer player, BlockPos pos) {
        if (!SREItemUtils.hasItem(player, DNFItems.CLEANING_BYPRODUCT)) {
            player.displayClientMessage(Component.translatable("message.dnf.task_point.exchange_need_byproduct")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            SREItemUtils.clearItem(player, DNFItems.CLEANING_BYPRODUCT, 1);
        }
        ItemStack emerald = new ItemStack(Items.EMERALD);
        if (!player.addItem(emerald)) {
            player.drop(emerald, false);
        }
        player.displayClientMessage(Component.translatable("message.dnf.task_point.exchange_success")
                .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 1.4f);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    8, 0.25, 0.25, 0.25, 0.0);
        }
        return InteractionResult.SUCCESS;
    }

    private static void ensureDisplayEntity(Level world, BlockPos pos) {
        if (world instanceof ServerLevel serverLevel) {
            DNFTaskPointEntity.spawnForBlock(serverLevel, pos);
        }
    }
}
