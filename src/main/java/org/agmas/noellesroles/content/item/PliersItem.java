package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.c4.C4Detonation;
import org.agmas.noellesroles.game.c4.PliersDefuseManager;
import org.jetbrains.annotations.NotNull;

public class PliersItem extends Item {
    public PliersItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player user, @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        if (!(entity instanceof Player target)) return InteractionResult.PASS;
        if (user.level().isClientSide) return InteractionResult.SUCCESS;
        return PliersDefuseManager.beginPlayerDefuse(stack, user, target, hand);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(user instanceof ServerPlayer serverUser)) return InteractionResultHolder.pass(stack);
        ItemEntity charge = C4Detonation.findLookedAtCharge(serverUser, 5.0D);
        if (charge == null) return InteractionResultHolder.pass(stack);
        InteractionResult result = PliersDefuseManager.beginBlockDefuse(serverUser, stack, charge, hand);
        return result.consumesAction() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }
}
