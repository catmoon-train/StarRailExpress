package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsDayCycle;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsManager;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 末日时钟：右键播报当前 天数 / 子相位 / 子相位剩余时间；持在手上（主/副手）时
 * 每秒在动作栏刷新时间（由 {@link #tickHeld} 驱动，服务端低频，无需同步字段）。
 */
public class SixtySecondsClockItem extends Item {
    public SixtySecondsClockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        serverPlayer.displayClientMessage(timeText(serverLevel), false);
        return InteractionResultHolder.success(stack);
    }

    /** 每秒对持钟玩家推送动作栏时间（在 {@link SixtySecondsManager#tick} 中调用）。 */
    public static void tickHeld(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        Component text = null;
        for (ServerPlayer player : level.players()) {
            if (player.getMainHandItem().getItem() instanceof SixtySecondsClockItem
                    || player.getOffhandItem().getItem() instanceof SixtySecondsClockItem) {
                if (text == null) {
                    text = timeText(level);
                }
                player.displayClientMessage(text, true);
            }
        }
    }

    private static Component timeText(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        long now = level.getGameTime();
        if (data.phase == SixtySecondsPhase.PREPARATION) {
            return Component.translatable("message.noellesroles.sixty_seconds.clock_prep",
                    mmss(Math.max(0, data.phaseEndTick - now))).withStyle(ChatFormatting.YELLOW);
        }
        if (data.phase != SixtySecondsPhase.DAY) {
            return Component.translatable("message.noellesroles.sixty_seconds.clock_inactive")
                    .withStyle(ChatFormatting.GRAY);
        }
        SixtySecondsDayCycle.SubPhase sub = SixtySecondsDayCycle.subPhase(data, now);
        Component subName = SixtySecondsDayCycle.isSleepWindow(data, now)
                ? Component.translatable("hud.noellesroles.sixty_seconds.subphase.sleep")
                : Component.translatable(sub.translationKey());
        return Component.translatable("message.noellesroles.sixty_seconds.clock_time",
                data.dayNumber, SixtySecondsManager.totalDays(level), subName,
                mmss(SixtySecondsDayCycle.subPhaseRemaining(data, now)))
                .withStyle(sub == SixtySecondsDayCycle.SubPhase.NIGHT ? ChatFormatting.BLUE : ChatFormatting.GOLD);
    }

    private static String mmss(long ticks) {
        long seconds = ticks / 20;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
