package io.wifi.events.day_night_fight.client.gui.clue;

import io.wifi.events.day_night_fight.cca.SREPlayerClueComponent;
import io.wifi.starrailexpress.client.InputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.init.ModEffects;

public final class ClueArchiveHud {
    private ClueArchiveHud() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui || client.screen != null
                || !player.hasEffect(ModEffects.GHOST_STATE)) {
            return;
        }

        Font font = client.font;
        SREPlayerClueComponent data = SREPlayerClueComponent.KEY.get(player);
        Component prompt = Component.translatable("hud.sre.clue_archive.prompt",
                InputHandler.getOpenClueArchiveKeybind().getTranslatedKeyMessage());
        Component count = Component.translatable("hud.sre.clue_archive.count",
                data.clues.size(), data.sendTimesLeft);
        int contentWidth = Math.max(font.width(prompt), font.width(count));
        int panelW = Math.max(132, contentWidth + 24);
        int panelH = 36;
        int x = client.getWindow().getGuiScaledWidth() - panelW - 14;
        int y = client.getWindow().getGuiScaledHeight() - panelH - 58;

        long tick = player.tickCount;
        int pulse = 26 + (int) (Math.sin(tick * 0.12) * 10.0);
        graphics.fill(x, y, x + panelW, y + panelH, 0xB20B1020);
        graphics.fill(x, y, x + 3, y + panelH, (0xA0 + pulse) << 24 | 0x62D8FF);
        graphics.fill(x + 3, y, x + panelW, y + 1, 0x5539C7FF);
        graphics.drawString(font, prompt, x + 12, y + 8, 0xDFF7FF, false);
        graphics.drawString(font, count, x + 12, y + 21, 0x97B6C6, false);
    }
}
