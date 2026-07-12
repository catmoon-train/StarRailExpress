package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * 末日60秒模式 HUD 占位：左上角显示「第 X/7 天 + 家庭身份 + 饥饿/口渴/san/污染/健康」条。
 * 参照 {@code net.exmo.sre.repair.client.RepairEscapeHud}（{@code CommonHudRenderCallback.EVENT}）。
 */
public final class SixtySecondsHud {
    private static final int BAR_W = 100;
    private static final int BAR_H = 8;

    private SixtySecondsHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isRunning()
                || SixtySecondsMod.MODE == null
                || SREClient.gameComponent.getGameMode() != SixtySecondsMod.MODE) {
            return;
        }
        LocalPlayer player = client.player;
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);

        int x = 8;
        int y = 8;
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.sixty_seconds.day",
                        Math.max(0, stats.dayNumber), SixtySecondsManager.TOTAL_DAYS),
                x, y, 0xFFFFFF55);
        y += 12;
        if (stats.familyPosition != null) {
            graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.sixty_seconds.family."
                            + stats.familyPosition.name().toLowerCase()),
                    x, y, 0xFFAAD4FF);
            y += 12;
        }
        y += 2;
        y = bar(graphics, client, x, y, "hunger", stats.hunger, 0xFFE0A020);
        y = bar(graphics, client, x, y, "thirst", stats.thirst, 0xFF3AA0E0);
        y = bar(graphics, client, x, y, "sanity", stats.sanity, 0xFFB060E0);
        y = bar(graphics, client, x, y, "pollution", stats.pollution, 0xFF6AA04A);
        bar(graphics, client, x, y, "health", stats.health, 0xFFE04040);
    }

    private static int bar(FakeGuiGraphics graphics, Minecraft client, int x, int y, String key, int value, int color) {
        graphics.fill(x, y, x + BAR_W, y + BAR_H, 0xAA000000);
        int fillW = Math.max(0, Math.min(BAR_W, (int) (BAR_W * (value / (double) SixtySecondsStatsComponent.MAX))));
        graphics.fill(x, y, x + fillW, y + BAR_H, color);
        graphics.drawString(client.font,
                Component.translatable("hud.noellesroles.sixty_seconds." + key), x + BAR_W + 4, y, 0xFFFFFFFF);
        return y + BAR_H + 2;
    }
}
