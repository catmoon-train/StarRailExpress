/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.loading;

import io.wifi.starrailexpress.SREClientConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 复古列车 / 车票 UI 公共绘制（docs/ui_style.md）。
 * <p>
 * 供资源加载、进世界、多人加入与连接中界面复用同一套配色、面板范式与入场动画。
 */
@Environment(EnvType.CLIENT)
public final class SreUiStyle {

    private SreUiStyle() {}

    // ── 色板（ui_style.md §2）────────────────────────────────────────
    public static final int PANEL_BG_TOP = 0xD81A1008;
    public static final int PANEL_BG_BOTTOM = 0xD820140A;
    public static final int SCREEN_BG_TOP = 0xF018120A;
    public static final int SCREEN_BG_BOTTOM = 0xF0061018;
    public static final int BORDER = 0xFF8B6914;
    public static final int DECOR = 0x33FFE8C0;
    public static final int GOLD = 0xFFD4AF37;
    public static final int GOLD_SOFT = 0xFFC9A84C;
    public static final int TEXT = 0xFFFFF4DC;
    public static final int TITLE = 0xFFF5E8C8;
    public static final int MUTED = 0xFF9E8B6E;
    public static final int BODY = 0xFFC8B898;

    public static final int JOIN_HEADER_H = 32;
    public static final int JOIN_FOOTER_H = 64;
    public static final long ENTER_MS = 380L;

    private static final float VIDEO_FPS = 20.0F;
    private static final FrameAnimationRenderer BACKDROP = new FrameAnimationRenderer(VIDEO_FPS);
    private static boolean backdropTried;

    public static void ensureBackdrop() {
        if (backdropTried) {
            return;
        }
        backdropTried = true;
        BACKDROP.loadFrames();
    }

    public static FrameAnimationRenderer backdrop() {
        ensureBackdrop();
        return BACKDROP;
    }

    public static float enterT(long openedAtMillis) {
        if (openedAtMillis < 0L) {
            return 1.0F;
        }
        return LoadingFx.easeOutCubic((Util.getMillis() - openedAtMillis) / (float) ENTER_MS);
    }

    // ── 全屏背景 ──────────────────────────────────────────────────────

    /**
     * 列车视频（无帧则深棕渐变）+ 暗金遮罩 + 金色微粒 + 暗角。
     */
    public static void renderMenuBackdrop(GuiGraphics g, int w, int h, float delta, float alpha) {
        renderBackdrop(g, w, h, delta, alpha, true);
    }

    /** 加载界面背景：同样的列车画面，但不铺金色微粒，避免抢进度条。 */
    public static void renderLoadingBackdrop(GuiGraphics g, int w, int h, float delta, float alpha) {
        renderBackdrop(g, w, h, delta, alpha, false);
    }

    private static void renderBackdrop(GuiGraphics g, int w, int h, float delta, float alpha, boolean goldDust) {
        ensureBackdrop();
        g.fill(0, 0, w, h, 0xFF000000);
        if (!SREClientConfig.instance().disableTitleScreenVideoBackground && BACKDROP.hasFrames()) {
            BACKDROP.render(g, w, h, delta, alpha);
        } else {
            g.fillGradient(0, 0, w, h,
                    LoadingFx.lerpArgb(alpha, 0x00000000, SCREEN_BG_TOP),
                    LoadingFx.lerpArgb(alpha, 0x00000000, SCREEN_BG_BOTTOM));
        }
        g.fillGradient(0, 0, w, h,
                LoadingFx.withAlpha(0x000000, 0.20F * alpha),
                LoadingFx.withAlpha(0x000010, 0.53F * alpha));
        g.fillGradient(0, h / 4, w, h, 0x00000000, LoadingFx.withAlpha(0x8B6914, 0.13F * alpha));
        if (goldDust) {
            drawGoldDust(g, w, h, alpha);
        }
        LoadingFx.drawVignette(g, w, h, alpha);
    }

    public static void drawGoldDust(GuiGraphics g, int w, int h, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        long time = Util.getMillis();
        int count = 16;
        for (int i = 0; i < count; i++) {
            float x = (time * 0.012F * (1 + i % 3) + i * 67.0F) % (w + 20) - 10;
            float y = h / 2.0F + (float) Math.sin(time * 0.0007 + i * 1.7) * (h * 0.42F);
            float twinkle = 0.5F + 0.5F * (float) Math.sin(time * 0.0011 + i * 2.3);
            int a = Mth.clamp((int) ((12 + 38 * twinkle) * alpha), 0, 255);
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, (a << 24) | 0x00D4AF37);
        }
    }

    // ── 面板（ui_style.md §3）──────────────────────────────────────────

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.01F) {
            return;
        }
        int top = LoadingFx.lerpArgb(alpha, 0x001A1008, PANEL_BG_TOP);
        int bot = LoadingFx.lerpArgb(alpha, 0x0020140A, PANEL_BG_BOTTOM);
        g.fillGradient(x, y, x + w, y + h, top, bot);
        int border = LoadingFx.lerpArgb(alpha, 0x008B6914, BORDER);
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, LoadingFx.lerpArgb(alpha, 0x00FFE8C0, DECOR));
    }

    public static void drawTitleUnderline(GuiGraphics g, int cx, int y, int halfWidth, float alpha) {
        int pulseA = (int) ((0.55F + 0.25F * (float) Math.sin(Util.getMillis() / 360.0)) * alpha * 255.0F);
        int color = (Mth.clamp(pulseA, 0, 255) << 24) | 0x00D4AF37;
        g.fill(cx - halfWidth, y, cx + halfWidth, y + 1, color);
    }

    // ── 多人加入页 ────────────────────────────────────────────────────

    public static void renderJoinBackground(GuiGraphics g, int w, int h, float delta, float enter) {
        renderMenuBackdrop(g, w, h, delta, 1.0F);
        int listTop = JOIN_HEADER_H;
        int listH = h - JOIN_FOOTER_H - JOIN_HEADER_H;
        float listAlpha = Mth.clamp((enter - 0.08F) / 0.72F, 0.0F, 1.0F);
        float footerAlpha = Mth.clamp((enter - 0.16F) / 0.72F, 0.0F, 1.0F);
        int listSlide = (int) ((1.0F - listAlpha) * 10.0F);
        int footerSlide = (int) ((1.0F - footerAlpha) * 16.0F);
        drawPanel(g, 0, listTop + listSlide, w, listH, listAlpha);
        int footerW = Math.min(w - 16, 320);
        int footerX = (w - footerW) / 2;
        drawPanel(g, footerX, h - JOIN_FOOTER_H + 4 + footerSlide, footerW, JOIN_FOOTER_H - 8, footerAlpha);
    }

    public static void drawJoinHeader(GuiGraphics g, Font font, int w, float enter) {
        float headerAlpha = Mth.clamp(enter / 0.85F, 0.0F, 1.0F);
        int slide = (int) ((1.0F - headerAlpha) * -12.0F);
        g.pose().pushPose();
        g.pose().translate(0.0F, slide, 0.0F);
        Component title = Component.translatable("screen.sre.join.title").withStyle(ChatFormatting.BOLD);
        Component sub = Component.translatable("screen.sre.join.subtitle");
        int titleY = 6;
        g.drawString(font, title, (w - font.width(title)) / 2, titleY,
                LoadingFx.withAlpha(0xF5E8C8, headerAlpha), false);
        g.drawString(font, sub, (w - font.width(sub)) / 2, titleY + 12,
                LoadingFx.withAlpha(0x9E8B6E, headerAlpha * 0.95F), false);
        drawTitleUnderline(g, w / 2, JOIN_HEADER_H - 3, Math.min(90, w / 5), headerAlpha);
        g.pose().popPose();
    }

    // ── 连接中 ────────────────────────────────────────────────────────

    public static void renderConnectBackground(GuiGraphics g, int w, int h, float delta, float enter) {
        renderMenuBackdrop(g, w, h, delta, 1.0F);
        int panelW = Math.min(420, (int) (w * 0.72F));
        int panelH = 118;
        int x = (w - panelW) / 2;
        int y = h / 2 - 86 + (int) ((1.0F - enter) * 14.0F);
        drawPanel(g, x, y, panelW, panelH, enter);

        int half = Math.min(panelW / 2 - 24, 160);
        int cx = w / 2;
        int railY = y + panelH - 22;
        float phase = (Util.getMillis() % 2600L) / 2600.0F;
        LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, enter);
    }

    public static void drawConnectStatus(GuiGraphics g, Font font, int w, int h, Component status, float enter) {
        float pulse = 0.70F + 0.30F * (float) Math.sin(Util.getMillis() / 180.0);
        Component title = Component.translatable("screen.sre.connect.title").withStyle(ChatFormatting.BOLD);
        int titleY = h / 2 - 72 + (int) ((1.0F - enter) * -8.0F);
        g.drawString(font, title, (w - font.width(title)) / 2, titleY,
                LoadingFx.withAlpha(0xF5E8C8, enter), false);
        drawTitleUnderline(g, w / 2, titleY + 12, 36, enter);
        g.drawString(font, status, (w - font.width(status)) / 2, h / 2 - 44,
                LoadingFx.withAlpha(0xFFF4DC, enter * pulse), false);
        Component hint = Component.translatable("screen.sre.connect.hint");
        g.drawString(font, hint, (w - font.width(hint)) / 2, h / 2 - 28,
                LoadingFx.withAlpha(0x9E8B6E, enter * 0.9F), false);
    }

    public static int blend(int c1, int c2, float t) {
        return LoadingFx.lerpArgb(t, c1, c2);
    }
}
