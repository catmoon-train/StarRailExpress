package io.wifi.events.day_night_fight;

import io.wifi.events.day_night_fight.block.DNFServingPlateBlock;
import io.wifi.events.day_night_fight.block.DNFWaterDispenserBlock;
import io.wifi.events.day_night_fight.block_entity.DNFServingPlateBlockEntity;
import io.wifi.events.day_night_fight.block_entity.DNFWaterDispenserBlockEntity;
import io.wifi.events.day_night_fight.cca.DNFPlayerComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

public class DNFChefIngredientItem extends Item {
    public static final int WORK_TICKS = 20 * 20;

    public DNFChefIngredientItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !DNF.isDayNightFightMode(world)
                || !DNF.isDNFChef(serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }
        ChefWorkTarget target = findTarget(serverPlayer, stack);
        if (target == ChefWorkTarget.NONE) {
            showHint(serverPlayer, stack);
            return InteractionResultHolder.fail(stack);
        }
        serverPlayer.startUsingItem(hand);
        world.playSound(null, serverPlayer.blockPosition(), SoundEvents.SMOKER_SMOKE,
                SoundSource.PLAYERS, 0.6f, 1.0f);
        serverPlayer.displayClientMessage(Component.translatable(target.startMessageKey)
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (world.isClientSide || !(user instanceof ServerPlayer player) || !DNF.isDayNightFightMode(world)) {
            return;
        }
        int chargedTicks = getUseDuration(stack, user) - remainingUseTicks;
        if (chargedTicks < WORK_TICKS) {
            player.displayClientMessage(Component.translatable("message.dnf.chef.work_cancelled")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ChefWorkTarget target = findTarget(player, stack);
        switch (target) {
            case PLATE_DETOX -> detoxPlate(player, stack);
            case WATER_DISPENSER_DETOX -> detoxWaterDispenser(player, stack);
            case SMOKER_COOK -> cookAtSmoker(player, stack);
            case NONE -> showHint(player, stack);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    private static ChefWorkTarget findTarget(ServerPlayer player, ItemStack stack) {
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (stack.is(DNFItems.CORNMEAL_BAG) && hit != null) {
            BlockPos pos = hit.getBlockPos();
            if (player.level().getBlockState(pos).getBlock() instanceof DNFServingPlateBlock
                    && player.level().getBlockEntity(pos) instanceof DNFServingPlateBlockEntity plate
                    && plate.getPoisoner() != null) {
                return ChefWorkTarget.PLATE_DETOX;
            }
            if (player.level().getBlockState(pos).getBlock() instanceof DNFWaterDispenserBlock
                    && player.level().getBlockEntity(pos) instanceof DNFWaterDispenserBlockEntity dispenser
                    && dispenser.isPoisoned()) {
                return ChefWorkTarget.WATER_DISPENSER_DETOX;
            }
        }
        if (player.isShiftKeyDown() && isCookableIngredient(stack) && findNearbySmoker(player) != null) {
            return ChefWorkTarget.SMOKER_COOK;
        }
        return ChefWorkTarget.NONE;
    }

    private static void detoxPlate(ServerPlayer player, ItemStack stack) {
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (hit == null || !(player.level().getBlockEntity(hit.getBlockPos()) instanceof DNFServingPlateBlockEntity plate)
                || plate.getPoisoner() == null) {
            showHint(player, stack);
            return;
        }
        if (!DNFPlayerComponent.KEY.get(player).useChefCapacity(player, 1)) {
            return;
        }
        consumeOne(player, stack);
        plate.setPoisoner(null);
        player.level().playSound(null, hit.getBlockPos(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 0.8f, 1.25f);
        player.displayClientMessage(Component.translatable("message.dnf.plate.detoxed")
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void detoxWaterDispenser(ServerPlayer player, ItemStack stack) {
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (hit == null || !(player.level().getBlockEntity(hit.getBlockPos()) instanceof DNFWaterDispenserBlockEntity dispenser)
                || !dispenser.isPoisoned()) {
            showHint(player, stack);
            return;
        }
        if (!DNFPlayerComponent.KEY.get(player).useChefCapacity(player, DNF.CHEF_WATER_CHECK_COST)) {
            return;
        }
        consumeOne(player, stack);
        dispenser.setPoisoner(null);
        player.level().playSound(null, hit.getBlockPos(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 0.8f, 1.25f);
        player.displayClientMessage(Component.translatable("message.dnf.water_dispenser.detoxed")
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void cookAtSmoker(ServerPlayer player, ItemStack stack) {
        Recipe recipe = recipeFor(stack);
        if (recipe == null || findNearbySmoker(player) == null) {
            showHint(player, stack);
            return;
        }
        if (!DNFPlayerComponent.KEY.get(player).useChefCapacity(player, recipe.outputCount)) {
            return;
        }
        consumeOne(player, stack);
        DNFItems.giveOrDrop(player, new ItemStack(recipe.output, recipe.outputCount));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMOKER_SMOKE,
                SoundSource.BLOCKS, 0.9f, 0.9f);
        player.displayClientMessage(Component.translatable(recipe.messageKey, recipe.outputCount)
                .withStyle(ChatFormatting.DARK_GREEN), true);
    }

    private static void consumeOne(ServerPlayer player, ItemStack stack) {
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }

    private static void showHint(ServerPlayer player, ItemStack stack) {
        String key = stack.is(DNFItems.CORNMEAL_BAG)
                ? "message.dnf.chef.ingredient_hint_cornmeal"
                : "message.dnf.chef.ingredient_hint_smoker";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
    }

    private static boolean isCookableIngredient(ItemStack stack) {
        return stack.is(DNFItems.CORNMEAL_BAG) || stack.is(DNFItems.FLOUR_BAG) || stack.is(DNFItems.SUSPICIOUS_MEAT);
    }

    private static Recipe recipeFor(ItemStack stack) {
        if (stack.is(DNFItems.CORNMEAL_BAG)) {
            return new Recipe(DNFItems.CORN_GRUEL, 10, "message.dnf.chef.smoker_corn_gruel");
        }
        if (stack.is(DNFItems.FLOUR_BAG)) {
            return new Recipe(DNFItems.CORN_GRUEL, 10, "message.dnf.chef.smoker_flour_gruel");
        }
        if (stack.is(DNFItems.SUSPICIOUS_MEAT)) {
            return new Recipe(DNFItems.MEAT_RATION, 1, "message.dnf.chef.smoker_meat");
        }
        return null;
    }

    private static BlockPos findNearbySmoker(ServerPlayer player) {
        BlockHitResult hit = DNF.findLookedAtBlock(player, 6.0);
        if (hit != null && player.level().getBlockState(hit.getBlockPos()).is(Blocks.SMOKER)) {
            return hit.getBlockPos();
        }
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (player.level().getBlockState(pos).is(Blocks.SMOKER)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private enum ChefWorkTarget {
        NONE(""),
        PLATE_DETOX("message.dnf.plate.detox_started"),
        WATER_DISPENSER_DETOX("message.dnf.water_dispenser.detox_started"),
        SMOKER_COOK("message.dnf.chef.smoker_started");

        private final String startMessageKey;

        ChefWorkTarget(String startMessageKey) {
            this.startMessageKey = startMessageKey;
        }
    }

    private record Recipe(Item output, int outputCount, String messageKey) {
    }
}
