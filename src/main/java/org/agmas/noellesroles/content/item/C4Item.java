package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.game.c4.C4Detonation;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

public class C4Item extends Item {
    public C4Item(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
            @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        if (entity.hasEffect(ModEffects.SAFE_TIME))
            return InteractionResult.PASS;
        if (player.hasEffect(ModEffects.SAFE_TIME))
            return InteractionResult.PASS;
        if (!(entity instanceof Player target))
            return InteractionResult.PASS;
        if (target == player)
            return InteractionResult.PASS;
        if (target.isSpectator())
            return InteractionResult.PASS;
        if (player.level().isClientSide)
            return InteractionResult.SUCCESS;

        return plantOnPlayer(stack, player, target);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.hasEffect(ModEffects.SAFE_TIME))
            return InteractionResultHolder.pass(stack);
        if (stack.isEmpty())
            return InteractionResultHolder.pass(stack);

        Player targetedPlayer = findTargetedPlayer(player);
        if (targetedPlayer != null) {
            if (targetedPlayer.hasEffect(ModEffects.SAFE_TIME))
                return InteractionResultHolder.pass(stack);
            if (!level.isClientSide) {
                plantOnPlayer(stack, player, targetedPlayer);
            }
            return InteractionResultHolder.success(stack);
        }

        if (!level.isClientSide) {
            ItemStack thrownStack = stack.copyWithCount(1);
            Vec3 eye = player.getEyePosition();
            Vec3 direction = player.getViewVector(1.0F).normalize();
            Vec3 velocity = direction.scale(0.85D).add(0.0D, 0.12D, 0.0D);
            Vec3 spawnPos = eye.add(direction.scale(0.5D));
            ItemEntity entity = new ItemEntity(level, spawnPos.x, spawnPos.y - 0.2D, spawnPos.z,
                    thrownStack, velocity.x, velocity.y, velocity.z);
            entity.setThrower(player);
            entity.setPickUpDelay(32767);
            entity.setUnlimitedLifetime();
            level.addFreshEntity(entity);
            C4Detonation.registerThrownCharge(entity, player.getUUID());
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.55F, 0.75F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResultHolder.success(stack);
    }

    private static InteractionResult plantOnPlayer(ItemStack stack, Player user, Player target) {
        if (!(user instanceof ServerPlayer serverUser))
            return InteractionResult.PASS;
        if (!(serverUser.level() instanceof ServerLevel level))
            return InteractionResult.PASS;

        C4BackComponent comp = C4BackComponent.KEY.getNullable(level);
        if (comp == null)
            return InteractionResult.FAIL;

        if (comp.hasC4(target.getUUID())) {
            user.displayClientMessage(Component.translatable("c4.already_on_player"), true);
            return InteractionResult.CONSUME;
        }

        if (!comp.addC4(target.getUUID(), user.getUUID()))
            return InteractionResult.FAIL;

        if (!user.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.8F, 1.2F);

        user.displayClientMessage(Component.translatable("c4.placed_on_player", target.getName().getString()), true);
        target.displayClientMessage(Component.translatable("c4.you_have_c4"), false);

        return InteractionResult.CONSUME;
    }

    private static Player findTargetedPlayer(Player user) {
        double range = Math.max(3.0D, user.entityInteractionRange());
        Vec3 start = user.getEyePosition();
        Vec3 direction = user.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(range));
        AABB searchBox = user.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0D);

        HitResult hit = user.pick(range, 0.0F, false);
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            if (entityHit.getEntity() instanceof Player target && canPlantOnEntity(user, target)) {
                return target;
            }
        }
        return null;
    }

    private static boolean canPlantOnEntity(Player user, Entity entity) {
        return entity instanceof Player target
                && target != user
                && !target.isSpectator()
                && target.canBeHitByProjectile();
    }
}
