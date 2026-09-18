package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role_data.innocence.WatchmanRoleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 梆（更夫专属，一次性道具）。
 *
 * <p>
 * 使用后持续 {@link WatchmanRoleData#BANG_DURATION_TICKS}：每 2 秒按周围玩家数量扣减游戏时间
 * （每名玩家 4 秒，单次最多 1 分钟），同时使周围玩家获得 1 分钟「入梦」效果。
 * 规则本体在 {@link WatchmanRoleData#useBang(ItemStack)}。
 */
public class BangItem extends Item {
    public BangItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        WatchmanRoleData data = RoleData.getNullable(WatchmanRoleData.class, serverPlayer);
        if (data == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.watchman.item_only")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }
        if (!data.useBang(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        stack.consume(1, serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
