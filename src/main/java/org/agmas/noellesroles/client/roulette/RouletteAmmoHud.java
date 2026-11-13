package org.agmas.noellesroles.client.roulette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.minigame.roulette.RoulettePhase;
import org.agmas.noellesroles.game.minigame.roulette.RouletteView;

/** 红色为已知实弹，灰色为已知空包；问号只表示逆转后的未公开弹型，不表示弹序。 */
final class RouletteAmmoHud {
    private static final ResourceLocation SHELLS = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "textures/gui/roulette/shells.png");
    private RouletteAmmoHud() {}
    static void render(GuiGraphics g, RouletteView view) {
        if (view.phase() == RoulettePhase.WAITING) return;
        int total = Math.clamp(view.remaining(), 0, 8);
        int live = Math.clamp(view.live(), 0, total);
        int blanks = Math.clamp(view.blanks(), 0, total - live);
        var box = RouletteAmmoLayout.right(g.guiWidth(), g.guiHeight(), total);
        if (box.width() < 60) return;
        rounded(g, box.x(), box.y(), box.width(), box.height(), 0xD0100E0B);
        g.fill(box.x() + 8, box.y() + 3, box.x() + box.width() - 8, box.y() + 4, 0x667F6636);
        RouletteHud.fitted(g, Component.translatable("roulette.hud.ammo_remaining", total),
                box.x() + 8, box.y() + 7, box.width() - 16, RouletteHud.MUTED);
        if (total == 0) {
            g.drawCenteredString(Minecraft.getInstance().font, Component.translatable("roulette.hud.ammo_empty"),
                    box.x() + box.width() / 2, box.y() + 30, RouletteHud.MUTED);
            return;
        }
        int left = box.x() + (box.width() - (total * RouletteAmmoLayout.STEP - 3)) / 2;
        for (int i = 0; i < total; i++) {
            int x = left + i * RouletteAmmoLayout.STEP, y = box.y() + 19;
            boolean unknown = i >= live + blanks;
            g.blit(SHELLS, x, y, RouletteAmmoLayout.ICON_WIDTH, RouletteAmmoLayout.ICON_HEIGHT,
                    i < live ? 0F : 64F, 0F, 64, 160, 128, 160);
            if (unknown) g.drawCenteredString(Minecraft.getInstance().font, "?", x + 6, y + 10, RouletteHud.GOLD);
        }
    }
    private static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 3, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, color);
        g.fill(x + 3, y + h - 1, x + w - 3, y + h, color);
    }
}
