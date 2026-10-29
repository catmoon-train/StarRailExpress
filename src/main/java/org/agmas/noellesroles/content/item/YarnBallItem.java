package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.YarnBallEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 毛线球（裁缝专属消耗品，可在裁缝商店购买）。
 *
 * <p>右键投掷出去（与雪球类似）；命中玩家时使目标获得 8 秒「腿瘸」效果
 * （{@link ModEffects#LIMP}）。掷空则掉落在原地可供拾回。
 * 规则本体（命中结算）在 {@link YarnBallEntity}。</p>
 */
public class YarnBallItem extends Item {

    /** 投掷初速度。 */
    public static final float THROW_SPEED = 1.5F;
    /** 投掷散布。 */
    public static final float THROW_INACCURACY = 1.0F;

    public YarnBallItem(Properties properties) {
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
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)
                || serverPlayer.hasEffect(ModEffects.USED_BANED)) {
            return InteractionResultHolder.fail(stack);
        }
        YarnBallEntity projectile = new YarnBallEntity(ModEntities.YARN_BALL, serverPlayer, serverPlayer.level());
        projectile.setPos(serverPlayer.getEyePosition());
        projectile.shootFromRotation(serverPlayer, serverPlayer.getXRot(), serverPlayer.getYRot(),
                0.0F, THROW_SPEED, THROW_INACCURACY);
        serverPlayer.level().addFreshEntity(projectile);
        stack.shrink(1);
        serverPlayer.swing(InteractionHand.MAIN_HAND, true);
        serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.7F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
