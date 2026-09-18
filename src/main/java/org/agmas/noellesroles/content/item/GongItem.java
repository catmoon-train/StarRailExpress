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
 * 锣（更夫专属，一次性道具）。
 *
 * <p>
 * 使用后警示周围玩家：使周围「非平民 / 非警长」阵营的玩家在
 * {@link WatchmanRoleData#GONG_BAN_TICKS} 内无法使用技能与背包（效果不带粒子、不显示气泡）。
 * 规则本体在 {@link WatchmanRoleData#useGong(ItemStack)}。
 */
public class GongItem extends Item {
    public GongItem(Properties properties) {
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
        if (!data.useGong(stack)) {
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
