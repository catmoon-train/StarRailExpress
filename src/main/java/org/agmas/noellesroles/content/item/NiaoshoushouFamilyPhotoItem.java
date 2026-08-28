/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.NiaoshoushouRoleData;

import java.util.UUID;

/**
 * 全家福：分别右键两名符合条件的玩家，成功后组成一次性队伍。
 */
public class NiaoshoushouFamilyPhotoItem extends Item {
    private static final String FIRST_MEMBER_TAG = "niaoshoushou_family_first";

    public NiaoshoushouFamilyPhotoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer owner) || owner.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }

        HitResult hit = ProjectileUtil.getHitResultOnViewVector(owner,
                entity -> entity instanceof ServerPlayer target
                        && target != owner
                        && GameUtils.isPlayerAliveAndSurvival(target), 4.0F);
        ServerPlayer target = hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayer serverTarget ? serverTarget : null;
        return selectMember(owner, stack, target)
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof ServerPlayer owner) || !(target instanceof ServerPlayer serverTarget)
                || owner.isSpectator()) {
            return InteractionResult.PASS;
        }
        return selectMember(owner, stack, serverTarget) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    private static boolean selectMember(ServerPlayer owner, ItemStack stack, ServerPlayer target) {
        NiaoshoushouRoleData data = NiaoshoushouRoleData.get(owner);
        if (data == null || data.isFamilyFormed() || target == null || target == owner
                || !GameUtils.isPlayerAliveAndSurvival(target) || !isAllowedMember(target)) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_invalid_target")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag();
        if (!tag.hasUUID(FIRST_MEMBER_TAG)) {
            tag.putUUID(FIRST_MEMBER_TAG, target.getUUID());
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            owner.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_first",
                    target.getName()).withStyle(ChatFormatting.YELLOW), true);
            return true;
        }

        UUID firstId = tag.getUUID(FIRST_MEMBER_TAG);
        if (firstId.equals(target.getUUID())) {
            owner.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_duplicate")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        MinecraftServer server = owner.getServer();
        ServerPlayer first = server == null ? null : server.getPlayerList().getPlayer(firstId);
        if (first == null || !isAllowedMember(first) || !GameUtils.isPlayerAliveAndSurvival(first)) {
            tag.remove(FIRST_MEMBER_TAG);
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            owner.displayClientMessage(Component.translatable("message.noellesroles.niaoshoushou.family_first_expired")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (!data.formFamily(owner, first, target)) {
            return false;
        }
        tag.remove(FIRST_MEMBER_TAG);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        if (!owner.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    private static boolean isAllowedMember(ServerPlayer target) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(target.level());
        SRERole role = gameWorld.getRole(target);
        return role != null && (role.isKillerTeam() || gameWorld.isRole(target, ModRoles.MAGICIAN));
    }
}
