package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.SlipperyGhostPlayerComponent;

/**
 * 空包弹物品
 * - 滑头鬼专属道具
 * - 右键对目标玩家使用
 * - 使目标手中的枪进入30秒冷却
 * - 使用后消耗
 */
public class BlankCartridgeItem extends Item {

    // 冷却时间: 30秒 = 600 ticks
    private static final int GUN_COOLDOWN_TICKS = 600;

    public BlankCartridgeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity,
            InteractionHand hand) {
        if (user.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 检查目标是否为玩家
        if (!(entity instanceof ServerPlayer target)) {
            return InteractionResult.PASS;
        }

        // 检查冷却
        SlipperyGhostPlayerComponent ghostComp = ModComponents.SLIPPERY_GHOST.get(user);
        if (ghostComp.isBlankCartridgeOnCooldown()) {
            user.displayClientMessage(
                    Component
                            .translatable("item.blank_cartridge.cooldown", ghostComp.getBlankCartridgeCooldownSeconds())
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }

        // 检查目标是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return InteractionResult.FAIL;
        }

        // 获取目标手中的物品，检查是否为枪械
        ItemStack targetMainHand = target.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack targetOffHand = target.getItemInHand(InteractionHand.OFF_HAND);

        boolean appliedCooldown = false;

        // 检查主手是否为枪械
        if (isGun(targetMainHand)) {
            target.getCooldowns().addCooldown(targetMainHand.getItem(), GUN_COOLDOWN_TICKS);
            appliedCooldown = true;
        }

        // 检查副手是否为枪械
        if (isGun(targetOffHand)) {
            target.getCooldowns().addCooldown(targetOffHand.getItem(), GUN_COOLDOWN_TICKS);
            appliedCooldown = true;
        }

        if (appliedCooldown) {
            // 播放音效
            user.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);

            // 通知目标
            target.displayClientMessage(
                    Component.translatable("item.blank_cartridge.effect.active.other").withStyle(ChatFormatting.RED),
                    true);

            // 通知使用者
            user.displayClientMessage(
                    Component.translatable("item.blank_cartridge.effect.active.info", target.getName())
                            .withStyle(ChatFormatting.GREEN),
                    true);

            // 消耗物品
            stack.consume(1, user);

            // 设置冷却
            ghostComp.setBlankCartridgeCooldown();

            return InteractionResult.SUCCESS;
        } else {
            // 目标没有枪械
            user.displayClientMessage(
                    Component.translatable("item.blank_cartridge.failed.no_gun.info").withStyle(ChatFormatting.YELLOW),
                    true);
            return InteractionResult.FAIL;
        }
    }

    /**
     * 检查物品是否为枪械
     */
    private boolean isGun(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        // 检查是否为原版枪械

        return stack.is(TMMItemTags.GUNS);
    }
}