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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 复古列车风格 —— 世界生成 / 区块加载界面（替换原版 LevelLoadingScreen）。
 * <p>
 * 与资源加载 {@link StarRailLoadingOverlay} 和进入世界 {@link SREReceivingLevelScreen}
 * 共用同一套视觉语言：全幅列车视频背景 + 暗角 + 金色星轨 + 轮换提示。
 */
@Environment(EnvType.CLIENT)
public class TrainLoadingScreen extends Screen {

    private static final long ENTER_MS = 650;
    private static final long END_HOLD_MS = 700;
    private static final long EXIT_MS = 600;
    private static final long SAFETY_TIMEOUT_MS = 30_000L;
    private static final long NARRATION_DELAY_MS = 2000L;
    private static final long TIP_INTERVAL_MS = 4200L;
    private static final long TIP_FADE_MS = 320L;
    private static final long ELLIPSIS_INTERVAL_MS = 500L;

    private final StoringChunkProgressListener progressListener;
    private final boolean hasProgressListener;
    private final BooleanSupplier levelReceived;
    private final long createdAt;

    private boolean done;
    private long exitStart = -1L;
    private float displayProgress;
    private long lastNarration = -1L;

    private final List<Component> tips;
    private int tipIndex;
    private int prevTipIndex;
    private long tipChangedAt;

    private int ellipsis;
    private long lastEllipsisAt;

    public TrainLoadingScreen(StoringChunkProgressListener progressListener, BooleanSupplier levelReceived) {
        super(Component.translatable("screen.starrailexpress.loading.title"));
        this.progressListener = progressListener;
        this.hasProgressListener = progressListener != null;
        this.levelReceived = levelReceived;
        this.createdAt = Util.getMillis();
        this.tips = List.of(
                Component.translatable("loading.tip.starrailexpress.1"),
                Component.translatable("loading.tip.starrailexpress.2"),
                Component.translatable("loading.tip.starrailexpress.3"),
                Component.translatable("loading.tip.starrailexpress.4"),
                Component.translatable("loading.tip.starrailexpress.5"));
        this.tipChangedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        SreUiStyle.registerLoadingBackground();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void removed() {
        this.done = true;
        this.triggerImmediateNarration(true);
    }

    @Override
    public void tick() {
        long now = Util.getMillis();

        if (exitStart < 0L
                && (this.levelReceived.getAsBoolean() || now - createdAt > SAFETY_TIMEOUT_MS)) {
            exitStart = now;
        }

        if (now - tipChangedAt > TIP_INTERVAL_MS) {
            prevTipIndex = tipIndex;
            tipIndex = (tipIndex + 1) % tips.size();
            tipChangedAt = now;
        }
        if (now - lastEllipsisAt > ELLIPSIS_INTERVAL_MS) {
            ellipsis = (ellipsis + 1) % 4;
            lastEllipsisAt = now;
        }
        if (now - lastNarration > NARRATION_DELAY_MS) {
            lastNarration = now;
            this.triggerImmediateNarration(true);
        }
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput out) {
        if (done) {
            out.add(NarratedElementType.TITLE, Component.translatable("narrator.loading.done"));
        } else if (hasProgressListener) {
            out.add(NarratedElementType.TITLE, getProgressComponent());
        } else {
            out.add(NarratedElementType.TITLE, Component.translatable("loading.world.generating"));
        }
    }

    private Component getProgressComponent() {
        int percent = Mth.clamp((int) (progressListener.getProgress() * 100), 0, 100);
        return Component.translatable("loading.progress", percent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        long now = Util.getMillis();

        float enterAlpha = LoadingFx.smoothstep((now - createdAt) / (float) ENTER_MS);
        float exitAlpha = 1.0F;
        float resolveT = 0.0F;
        boolean resolving = exitStart >= 0L;
        if (resolving) {
            long since = now - exitStart;
            resolveT = LoadingFx.smoothstep(since / (float) END_HOLD_MS);
            if (since >= END_HOLD_MS) {
                long ex = since - END_HOLD_MS;
                exitAlpha = 1.0F - LoadingFx.smoothstep(ex / (float) EXIT_MS);
                if (ex >= EXIT_MS) {
                    onClose();
                    return;
                }
            }
        }
        float alpha = enterAlpha * exitAlpha;

        SreUiStyle.renderLoadingBackdrop(g, w, h, delta, alpha);

        float real = hasProgressListener ? LoadingFx.clamp01(progressListener.getProgress()) : 0.0F;
        float target = resolving ? Math.max(real, resolveT) : real;
        displayProgress += (target - displayProgress) * 0.12F;
        if (Math.abs(target - displayProgress) < 0.002F) {
            displayProgress = target;
        }

        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 70;

        if (hasProgressListener || resolving) {
            LoadingFx.drawRail(g, cx - half, cx + half, railY, displayProgress, alpha);
            String percent = (int) (displayProgress * 100) + "%";
            g.drawString(font, percent, cx - font.width(percent) / 2, railY - 16,
                    LoadingFx.withAlpha(0xFFF4DC, alpha), false);
        } else {
            float phase = (now % 2600L) / 2600.0F;
            LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, alpha);
            String text = Component.translatable("loading.world.generating").getString()
                    + ".".repeat(ellipsis);
            g.drawString(font, text, cx - font.width(text) / 2, railY - 16,
                    LoadingFx.withAlpha(0xC8B898, alpha), false);
        }

        drawTips(g, cx, railY + 14, alpha, now);
    }

    private void drawTips(GuiGraphics g, int cx, int y, float alpha, long now) {
        float fade = LoadingFx.smoothstep((now - tipChangedAt) / (float) TIP_FADE_MS);
        if (fade < 1.0F && prevTipIndex != tipIndex) {
            drawTipLine(g, tips.get(prevTipIndex).getString(), cx, y - (int) (fade * 6.0F),
                    alpha * (1.0F - fade) * 0.85F);
        }
        drawTipLine(g, tips.get(tipIndex).getString(), cx, y + (int) ((1.0F - fade) * 6.0F),
                alpha * fade * 0.85F);
    }

    private void drawTipLine(GuiGraphics g, String text, int cx, int y, float a) {
        if (a <= 0.01F) {
            return;
        }
        g.drawString(font, text, cx - font.width(text) / 2, y,
                LoadingFx.withAlpha(0x9E8B6E, a), false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xFF000000);
    }
}
