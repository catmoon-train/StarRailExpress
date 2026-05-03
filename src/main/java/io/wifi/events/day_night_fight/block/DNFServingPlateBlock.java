package io.wifi.events.day_night_fight.block;

import com.mojang.serialization.MapCodec;
import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.block_entity.DNFServingPlateBlockEntity;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DNFServingPlateBlock extends BaseEntityBlock {
    public static final MapCodec<DNFServingPlateBlock> CODEC = simpleCodec(DNFServingPlateBlock::new);

    public DNFServingPlateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DNFServingPlateBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return getShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getShape(state);
    }

    protected VoxelShape getShape(BlockState state) {
        return box(0, 0, 0, 16, 2, 16);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof DNFServingPlateBlockEntity plate)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(DNFItems.CORNMEAL_BAG) && DNF.isDNFChef(player) && plate.getPoisoner() != null) {
            player.startUsingItem(hand);
            player.displayClientMessage(Component.translatable("message.dnf.plate.detox_started")
                    .withStyle(ChatFormatting.AQUA), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (tryPoison(world, pos, player, plate)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!DNFItems.isDnfFood(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!DNF.isDNFChef(player)) {
            player.displayClientMessage(Component.translatable("message.dnf.plate.chef_only")
                    .withStyle(ChatFormatting.YELLOW), true);
            return ItemInteractionResult.FAIL;
        }
        if (!plate.canAddFood()) {
            player.displayClientMessage(Component.translatable("message.dnf.plate.full")
                    .withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.FAIL;
        }

        ItemStack stored = stack.copy();
        stored.setCount(1);
        plate.addItem(stored);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        world.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
        player.displayClientMessage(Component.translatable("message.dnf.plate.food_added")
                .withStyle(ChatFormatting.GREEN), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, @NotNull Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof DNFServingPlateBlockEntity plate)) {
            return InteractionResult.PASS;
        }
        if (tryPoison(world, pos, player, plate)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        String poisoner = plate.getPoisoner();
        ItemStack food = plate.takeFood();
        if (food.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dnf.plate.empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        
        // 检查玩家今天是否还能拿取食物
        if (!DNFPlayerComponent.KEY.get(player).canTakeFoodToday((ServerPlayer) player)) {
            player.displayClientMessage(Component.translatable("message.dnf.plate.food_limit_reached")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.FAIL;
        }

        if (poisoner != null) {
            food.set(SREDataComponentTypes.POISONER, poisoner);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.9f, 1.1f);
        player.displayClientMessage(Component.translatable("message.dnf.plate.food_taken")
                .withStyle(ChatFormatting.GREEN), true);
        
        // 标记玩家今天拿取了一次食物（即使是厨师也要记录，虽然不限制）
        DNFPlayerComponent.KEY.get(player).markFoodTakenToday((ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    private static boolean tryPoison(Level world, BlockPos pos, Player player, DNFServingPlateBlockEntity plate) {
        if (!DNF.isDNFPoisoner(player)) {
            return false;
        }
        if (!DNF.isNight(player)) {
            return false;
        }
        if (!plate.hasFood()) {
            player.displayClientMessage(Component.translatable("message.dnf.plate.no_food_to_poison")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (plate.getPoisoner() != null) {
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.already_poisoned")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        plate.setPoisoner(player.getStringUUID());
        world.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dnf.plate.poisoned")
                .withStyle(ChatFormatting.DARK_GREEN), true);
        return true;
    }
}
