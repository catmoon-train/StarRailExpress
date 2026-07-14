package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 实体游戏币（1 枚 = 1 游戏币）：把个人游戏币余额「物化」的实体货币。
 * <ul>
 *   <li>兑出：E 背包「兑换实体币」按钮 → {@code TokenExchangeScreen} →
 *       {@code TokenExchangeC2SPacket}（余额 → 实体币）。</li>
 *   <li>存回：<b>右键使用</b>把手中整组存回自己的余额
 *       （{@link SREPlayerMinigameTaskComponent#addTokens}，按玩家独立，CCA 自动同步）。</li>
 * </ul>
 * 游戏币不再全队共享后，实体币是队内/跨队转账的手段（可直接丢给队友或经拜访「交易」换给别队）。
 */
public class SixtySecondsCoinItem extends Item {

    public SixtySecondsCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return InteractionResultHolder.consume(stack);
        }
        int amount = stack.getCount();
        SREPlayerMinigameTaskComponent tokens = SREPlayerMinigameTaskComponent.KEY.get(player);
        tokens.addTokens(amount);
        stack.shrink(amount);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.coin_deposited", amount, tokens.getTokens()), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResultHolder.consume(stack);
    }
}
