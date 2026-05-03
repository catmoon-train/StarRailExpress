package io.wifi.events.day_night_fight.block;

import com.mojang.serialization.MapCodec;
import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.DNFItems;
import io.wifi.events.day_night_fight.block_entity.DNFWaterDispenserBlockEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DNFWaterDispenserBlock extends BaseEntityBlock {
    public static final MapCodec<DNFWaterDispenserBlock> CODEC = simpleCodec(DNFWaterDispenserBlock::new);

    public DNFWaterDispenserBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DNFWaterDispenserBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(2, 0, 2, 14, 16, 14);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof DNFWaterDispenserBlockEntity dispenser)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(DNFItems.CORNMEAL_BAG) && DNF.isDNFChef(player) && dispenser.isPoisoned()) {
            player.startUsingItem(hand);
            player.displayClientMessage(Component.translatable("message.dnf.water_dispenser.detox_started")
                    .withStyle(ChatFormatting.AQUA), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (tryPoison(world, pos, player, dispenser)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.FAIL;
        }
        if (!DNF.isDayNightFightMode(world)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.clothes.dnf_only")
                    .withStyle(ChatFormatting.YELLOW), true);
            return ItemInteractionResult.FAIL;
        }
        // 检查玩家今天是否还能使用饮水机
        if (!DNFPlayerComponent.KEY.get(serverPlayer).canUseWaterDispenserToday(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message.dnf.water_dispenser.limit_reached")
                    .withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.FAIL;
        }
        ItemStack water = DNFItems.createWaterBottle(serverPlayer, 1);
        if (dispenser.getPoisoner() != null) {
            water.set(SREDataComponentTypes.POISONER, dispenser.getPoisoner());
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        DNFItems.giveOrDrop(serverPlayer, water);
        // 标记玩家今天已经使用过饮水机
        DNFPlayerComponent.KEY.get(serverPlayer).markWaterDispenserUsedToday(serverPlayer);
        world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.9f, 1.1f);
        player.displayClientMessage(Component.translatable("message.dnf.water_dispenser.filled")
                .withStyle(ChatFormatting.AQUA), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof DNFWaterDispenserBlockEntity dispenser
                && tryPoison(world, pos, player, dispenser)) {
            return InteractionResult.SUCCESS;
        }
        player.displayClientMessage(Component.translatable("message.dnf.water_dispenser.need_bottle")
                .withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.SUCCESS;
    }

    private static boolean tryPoison(Level world, BlockPos pos, Player player, DNFWaterDispenserBlockEntity dispenser) {
        if (!DNF.isDNFPoisoner(player) || !DNF.isNight(player)) {
            return false;
        }
        if (dispenser.isPoisoned()) {
            player.displayClientMessage(Component.translatable("message.dnf.poisoner.already_poisoned")
                    .withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        dispenser.setPoisoner(player.getStringUUID());
        world.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dnf.water_dispenser.poisoned")
                .withStyle(ChatFormatting.DARK_GREEN), true);
        return true;
    }
}
