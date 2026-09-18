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

package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class ScopeOverlayRenderer {

    /** 开镜约 160ms，关镜约 110ms。 */
    private static final float SCOPE_IN_SECONDS = 0.16f;
    private static final float SCOPE_OUT_SECONDS = 0.11f;
    /** 满开镜 FOV 为原值的 1/3（约 3x）。 */
    private static final float SCOPED_FOV_MULTIPLIER = 1f / 3f;
    /** 开镜后鼠标灵敏度，无电影视角滤波，只有倍率缩放。 */
    private static final float SCOPED_LOOK_MULTIPLIER = 0.42f;
    private static final float HAND_HIDE_PROGRESS = 0.38f;
    private static final float CROSSHAIR_HIDE_PROGRESS = 0.18f;

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private static boolean wantScoped = false;
    private static float progress = 0f;
    private static long lastNanos = 0L;

    public static boolean isInScopeView() {
        advance();
        return wantScoped || progress > 0.001f;
    }

    public static boolean isWantScoped() {
        return wantScoped;
    }

    public static void setInScopeView(boolean inScopeView) {
        if (wantScoped == inScopeView) {
            return;
        }
        wantScoped = inScopeView;
        playToggleSound(inScopeView);
    }

    /** 切枪 / 卸镜时立刻关掉，避免倍镜残留在别的物品上。 */
    public static void forceClose() {
        wantScoped = false;
        progress = 0f;
        lastNanos = 0L;
    }

    public static float getScopeProgress() {
        advance();
        return progress;
    }

    public static float getFovMultiplier() {
        float eased = easeOutCubic(getScopeProgress());
        return Mth.lerp(eased, 1f, SCOPED_FOV_MULTIPLIER);
    }

    public static float getLookSensitivityMultiplier() {
        float eased = easeOutCubic(getScopeProgress());
        return Mth.lerp(eased, 1f, SCOPED_LOOK_MULTIPLIER);
    }

    public static boolean shouldHideHands() {
        return getScopeProgress() >= HAND_HIDE_PROGRESS;
    }

    public static boolean shouldHideCrosshair() {
        return getScopeProgress() >= CROSSHAIR_HIDE_PROGRESS;
    }

    public static void renderScopeOverlay(GuiGraphics context, DeltaTracker tickCounter) {
        advance();
        if (progress <= 0.001f) {
            return;
        }

        float appear = easeOutCubic(progress);
        float alpha = Mth.clamp(progress / 0.34f, 0f, 1f);
        float lensScale = Mth.lerp(appear, 1.12f, 1.0f);

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        context.pose().pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        renderLensMask(context, lensScale, alpha);
        renderSimpleCross(context, centerX, centerY, alpha);

        context.pose().popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void advance() {
        long now = System.nanoTime();
        if (lastNanos == 0L) {
            lastNanos = now;
            return;
        }
        float dt = (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;
        if (dt <= 0f) {
            return;
        }
        dt = Math.min(dt, 0.05f);
        if (wantScoped) {
            progress = Math.min(1f, progress + dt / SCOPE_IN_SECONDS);
        } else if (progress > 0f) {
            progress = Math.max(0f, progress - dt / SCOPE_OUT_SECONDS);
        }
    }

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static void playToggleSound(boolean opening) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.playSound(
                opening ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING,
                0.8f,
                opening ? 0.82f : 1.08f);
    }

    private static void renderLensMask(GuiGraphics context, float scale, float alpha) {
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        int radius = Math.max(16, Mth.floor(Math.min(screenWidth, screenHeight) * 0.42f * scale));
        int black = withAlpha(BLACK, alpha);

        context.fill(0, 0, screenWidth, Math.max(0, cy - radius), black);
        context.fill(0, Math.min(screenHeight, cy + radius), screenWidth, screenHeight, black);

        int y0 = Math.max(0, cy - radius);
        int y1 = Math.min(screenHeight, cy + radius);
        for (int y = y0; y < y1; y++) {
            int dy = y - cy;
            int dx = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
            int left = cx - dx;
            int right = cx + dx;
            if (left > 0) {
                context.fill(0, y, left, y + 1, black);
            }
            if (right < screenWidth) {
                context.fill(right, y, screenWidth, y + 1, black);
            }
        }
    }

    /** 黑描边白十字，中心留空加白点。 */
    private static void renderSimpleCross(GuiGraphics context, int cx, int cy, float alpha) {
        int black = withAlpha(BLACK, alpha);
        int white = withAlpha(WHITE, alpha);
        int arm = 18;
        int gap = 3;

        hLine(context, cx - arm, cx - gap, cy, black, 3);
        hLine(context, cx + gap, cx + arm, cy, black, 3);
        vLine(context, cy - arm, cy - gap, cx, black, 3);
        vLine(context, cy + gap, cy + arm, cx, black, 3);

        hLine(context, cx - arm, cx - gap, cy, white, 1);
        hLine(context, cx + gap, cx + arm, cy, white, 1);
        vLine(context, cy - arm, cy - gap, cx, white, 1);
        vLine(context, cy + gap, cy + arm, cx, white, 1);

        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, black);
        context.fill(cx, cy, cx + 1, cy + 1, white);
    }

    private static void hLine(GuiGraphics context, int x1, int x2, int y, int color, int thickness) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int half = thickness / 2;
        context.fill(minX, y - half, maxX, y + half + (thickness % 2), color);
    }

    private static void vLine(GuiGraphics context, int y1, int y2, int x, int color, int thickness) {
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int half = thickness / 2;
        context.fill(x - half, minY, x + half + (thickness % 2), maxY, color);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f)), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
