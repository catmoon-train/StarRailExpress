/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.traindummy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Predicate;

/**
 * 列车假人工具。右键打开生成界面，蹲下右键清除身边 4 格内的假人。
 * 左键清除由假人的 {@code hurt} 处理。
 */
public class TrainDummyToolItem extends Item {

    public static Predicate<Player> openScreenCallback = null;

    public TrainDummyToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user.isShiftKeyDown()) {
            if (!world.isClientSide() && user instanceof ServerPlayer serverPlayer) {
                TrainDummyEntity.clearNear(serverPlayer, 4.0D);
            }
            return InteractionResultHolder.success(stack);
        }
        if (world.isClientSide() && openScreenCallback != null && openScreenCallback.test(user)) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.starrailexpress.train_dummy_tool.tooltip"));
    }
}
