package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.PlayerStaminaGetter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 石粒架
 * - 参考薄荷糖写法实现的食物
 * - 食用后恢复基于体力上限 50% 的体力
 */
public class ShilijiaItem extends Item {

    /** 恢复的体力比例（基于体力上限） */
    public static final float STAMINA_RESTORE_RATIO = 0.5f;

    public ShilijiaItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        // 检查游戏是否正在进行
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 开始使用（吃）
        user.startUsingItem(hand);

        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level world,
            @NotNull LivingEntity user) {
        if (user instanceof Player player) {
            if (!world.isClientSide()) {
                // 恢复基于体力上限 50% 的体力
                if (!(player instanceof PlayerStaminaGetter stamina)) {
                    return shrinkAndReturn(stack);
                }
                if (ModEffects.hasInfiniteStamina(player)) {
                    return shrinkAndReturn(stack);
                }

                SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
                float max = -1f;
                if (gameComponent != null && gameComponent.isRunning()) {
                    SRERole role = gameComponent.getRole(player);
                    if (role != null) {
                        int maxSprintTime = role.getMaxSprintTime(player);
                        if (maxSprintTime >= 0 && maxSprintTime != Integer.MAX_VALUE) {
                            max = maxSprintTime * ModEffects.getStaminaCapacityMultiplier(player);
                        }
                    }
                }

                if (max > 0f) {
                    float current = stamina.starrailexpress$getStamina();
                    if (current < 0) {
                        current = max; // -1 = 尚未初始化，视为满
                    }
                    current = Math.min(current, max);
                    float restored = max * STAMINA_RESTORE_RATIO;
                    stamina.starrailexpress$setStamina(Math.min(max, current + restored));
                }

                // 播放吃东西的音效
                world.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, 1.0F);
            }

            // 消耗物品
            stack.shrink(1);
        }

        return stack;
    }

    private static @NotNull ItemStack shrinkAndReturn(@NotNull ItemStack stack) {
        stack.shrink(1);
        return stack;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.shilijia.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return 32; // 标准食物食用时间
    }
}
