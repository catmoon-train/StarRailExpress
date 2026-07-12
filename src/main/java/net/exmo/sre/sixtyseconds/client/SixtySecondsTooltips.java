package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsConsumables;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 末日60秒模式：在食物/水物品的 tooltip 追加恢复值（仅本模式激活时显示）。
 * 参照 {@code io.wifi.starrailexpress.client.util.TMMItemTooltips}。
 */
public final class SixtySecondsTooltips {
    private SixtySecondsTooltips() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (SREClient.gameComponent == null || SixtySecondsMod.MODE == null
                    || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
                return;
            }
            int thirst = SixtySecondsConsumables.thirstRestoreOf(stack);
            if (thirst > 0) {
                lines.add(Component.translatable("tooltip.noellesroles.sixty_seconds.thirst", thirst)
                        .withStyle(ChatFormatting.AQUA));
                return;
            }
            int hunger = SixtySecondsConsumables.hungerRestoreOf(stack);
            if (hunger > 0) {
                lines.add(Component.translatable("tooltip.noellesroles.sixty_seconds.hunger", hunger)
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }
}
