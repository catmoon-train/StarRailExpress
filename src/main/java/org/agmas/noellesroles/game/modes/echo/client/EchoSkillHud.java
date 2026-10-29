package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 专长缩成底部一行：本人图标、名称与服务器冷却，不占视野中央。 */
public final class EchoSkillHud {
    private EchoSkillHud() { }
    public static void initialize() {
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> render(g));
    }
    private static void render(FakeGuiGraphics g) {
        var client = Minecraft.getInstance();
        var data = EchoHud.ownData();
        if (data == null || client.screen != null) return;
        var rect = new EchoHudLayout(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()).utility();
        int x = rect.x() + rect.width() / 2, y = rect.y(), width = rect.width() / 2;
        boolean usable = data.life().standing() && EchoHud.view().state().phase().acceptsActions() && !EchoHud.view().state().paused();
        long remaining = data.skillRemaining();
        int color = !usable ? 0xFF9E8B6E : remaining > 0 ? 0xFF5EB7D8 : 0xFF72C17B;
        g.blit(ResourceLocation.parse(data.skill().icon()), x, y, 12, 12, 0F, 0F, 32, 32, 32, 32);
        Component status = !usable ? EchoHud.tr("compact.unavailable") : remaining > 0
                ? EchoHud.tr("compact.cooldown", (remaining + 19) / 20) : EchoHud.tr("compact.ready");
        int statusWidth = 48;
        EchoHud.row(g, Component.translatable(data.skill().nameKey()), x + 16, y + 1, width - statusWidth - 20, color);
        EchoHud.row(g, status, x + width - statusWidth, y + 1, statusWidth - 3, color);
        if (remaining > 0) {
            int fill = (int) ((width - 16) * Math.max(0, 1D - remaining / (double) Math.max(1, data.skill().cooldownTicks())));
            g.fill(x + 16, y + 11, x + width, y + 12, 0xFF49341E);
            g.fill(x + 16, y + 11, x + 16 + fill, y + 12, color);
        }
    }
}
