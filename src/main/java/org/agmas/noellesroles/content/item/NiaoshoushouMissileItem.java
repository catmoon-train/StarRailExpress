/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.NiaoshoushouMissileEntity;
import org.agmas.noellesroles.init.ModEntities;

/** 鸟兽兽巡飞弹：发射后把使用者的相机绑定到弹体。 */
public class NiaoshoushouMissileItem extends Item {
    private static final int COOLDOWN_TICKS = 60 * 20;

    public NiaoshoushouMissileItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer owner) || !GameUtils.isPlayerAliveAndSurvival(owner)) {
            return InteractionResultHolder.fail(stack);
        }

        NiaoshoushouMissileEntity missile = new NiaoshoushouMissileEntity(ModEntities.NIAOSHOU_SHOU_MISSILE, level);
        missile.setOwner(owner);
        Vec3 launchDirection = owner.getLookAngle().normalize();
        missile.setPos(owner.getX() + launchDirection.x * 0.8D,
                owner.getEyeY() - 0.1D + launchDirection.y * 0.8D,
                owner.getZ() + launchDirection.z * 0.8D);
        missile.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, 0.9F, 0.0F);
        level.addFreshEntity(missile);
        // 相机绑定由导弹实体在首个服务端 tick 发送，避免生成包与相机包乱序导致绑定失败。
        if (!owner.isCreative()) {
            stack.shrink(1);
            owner.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.consume(stack);
    }
}
