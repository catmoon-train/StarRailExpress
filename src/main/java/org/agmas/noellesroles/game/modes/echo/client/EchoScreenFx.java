package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

/**
 * 屏幕状态特效：用四边渐变近似暗角，不引入后处理链。
 * 只读本人投影的生命/控制状态，任何数值都不参与判定。
 */
public final class EchoScreenFx {
    private static EchoLifeState lastLife;
    private static long escapedAt;
    private EchoScreenFx() { }

    public static void initialize() {
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> render(g));
    }

    private static void render(FakeGuiGraphics g) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) return;
        var data = EchoBagClient.data();
        if (data == null || data.matchId() == null || data.faction() == null) { lastLife = null; escapedAt = 0; return; }
        EchoLifeState life = data.life();
        if (life == EchoLifeState.ESCAPED && lastLife != EchoLifeState.ESCAPED) escapedAt = System.currentTimeMillis();
        lastLife = life;
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        if (data.controlled()) vignette(g, width, height, 0xFF2F6BD8, 0.08F);
        switch (life) {
            case WOUNDED -> vignette(g, width, height, 0xFF8B1A12, 0.10F * (0.7F + 0.3F * EchoAnim.breathe(1200)));
            case DOWNED -> {
                long bleed = data.bleedRemaining();
                float pulse = EchoAnim.breathe(bleed > 200 ? 1000 : 500);
                vignette(g, width, height, 0xFF8B1A12, 0.22F * (0.75F + 0.25F * pulse));
                g.fill(0, 0, width, height, EchoAnim.fade(0xFF000000, 0.10F + 0.06F * pulse));
            }
            default -> { }
        }
        if (escapedAt > 0) {
            long elapsed = System.currentTimeMillis() - escapedAt;
            if (elapsed >= 600) escapedAt = 0;
            else g.fill(0, 0, width, height, EchoAnim.fade(0xFFFFFFFF, 0.35F * (1F - elapsed / 600F)));
        }
    }

    private static void vignette(FakeGuiGraphics g, int width, int height, int argb, float strength) {
        if (strength <= 0F) return;
        int band = Math.max(12, height / 6);
        int edge = EchoAnim.fade(argb, strength);
        g.fillGradient(0, 0, width, band, edge, 0x00000000);
        g.fillGradient(0, height - band, width, height, 0x00000000, edge);
        int side = Math.max(10, width / 8);
        int strips = 8;
        for (int i = 0; i < strips; i++) {
            int color = EchoAnim.fade(argb, strength * (1F - i / (float) strips));
            int left = i * side / strips, right = (i + 1) * side / strips;
            g.fill(left, 0, right, height, color);
            g.fill(width - right, 0, width - left, height, color);
        }
    }
}
