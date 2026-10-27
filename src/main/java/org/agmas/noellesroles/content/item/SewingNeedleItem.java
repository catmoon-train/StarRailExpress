package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role_data.innocence.TailorRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 缝纫针（裁缝专属）。
 *
 * <p>右键一名玩家开始编织：<b>长按右键 5 秒</b>后若裁缝快捷栏里有毛线球，则消耗一个毛线球并为
 * 该玩家编织一件「裁缝的衣服」（自动穿上或进入其空余快捷栏）。编织期间目标必须保持存活、
 * 在 {@link TailorRoleData#WEAVE_RANGE} 格内、可见且被瞄准，否则中断。规则本体在
 * {@link TailorRoleData}。</p>
 *
 * <p>交互通过 {@code interactLivingEntity}（右键实体）完成：客户端会本地预测该调用，
 * 因此 {@link #startUsingItem} 在双端都会执行——客户端进入使用状态以获得长按动画与松手检测，
 * 服务端则驱动 {@code onUseTick} 推进编织。</p>
 */
public class SewingNeedleItem extends Item {

    public SewingNeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
            @NotNull LivingEntity entity, @NotNull InteractionHand hand) {
        if (!(entity instanceof Player)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            // 客户端本地进入使用状态（长按动画 + 松手发送 RELEASE_USE_ITEM），具体校验在服务端
            player.startUsingItem(hand);
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ServerPlayer target)) {
            return InteractionResult.PASS;
        }
        if (!SREGameWorldComponent.KEY.get(serverPlayer.level()).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        TailorRoleData data = RoleData.getNullable(TailorRoleData.class, serverPlayer);
        if (data == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.item_only").withStyle(ChatFormatting.RED),
                    true);
            return InteractionResult.FAIL;
        }
        if (!data.beginWeave(serverPlayer, target, hand)) {
            return InteractionResult.FAIL;
        }
        serverPlayer.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack,
            int remainingUseTicks) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        TailorRoleData data = RoleData.getNullable(TailorRoleData.class, serverPlayer);
        if (data == null) {
            return;
        }
        data.tickWeave(serverPlayer, getUseDuration(stack, entity) - remainingUseTicks);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity,
            int remainingUseTicks) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 松手（或使用被中断）即取消编织，不产生任何消耗
        TailorRoleData data = RoleData.getNullable(TailorRoleData.class, serverPlayer);
        if (data != null) {
            data.cancelWeave();
        }
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 72000;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
