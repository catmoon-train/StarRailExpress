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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 复古列车风格的资源加载覆盖层（替换原版 Mojang Logo / 资源重载界面）。
 * <p>
 * 背景使用静态 background.png，叠加暗角、金色星轨进度条与轮换提示。
 * 加载中淡入；资源就绪后立即交接，不为完成动画阻塞主菜单、进服或转服。
 */
@Environment(EnvType.CLIENT)
public class StarRailLoadingOverlay extends Overlay {

    // ── 时间线（毫秒） ────────────────────────────────────────
    private static final long ENTER_MS = 650;
    private static final long TIP_INTERVAL_MS = 4200;
    private static final long TIP_FADE_MS = 320;

    // 资源加载阶段语言文件未必就绪，提示固定用拉丁文以保证可渲染。
    private static final List<String> TIPS = List.of(
            "Calibrating star rail navigation",
            "Warming up the warp drive",
            "Synchronizing galactic coordinates",
            "All carriages standing by",
            "Plotting course across the Star Ocean"
    );

    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;

    private long startMillis = -1L;
    private boolean finished;
    private float displayProgress;

    private int tipIndex;
    private int prevTipIndex;
    private long tipChangedAt;

    public StarRailLoadingOverlay(Minecraft mc, ReloadInstance reloader,
                                  Consumer<Optional<Throwable>> errorConsumer, boolean fadeIn) {
        this.minecraft = mc;
        this.reload = reloader;
        this.onFinish = errorConsumer;
        this.fadeIn = fadeIn;
        this.tipChangedAt = Util.getMillis();
        SreUiStyle.registerLoadingBackground();
    }

    public static void registerTextures(Minecraft minecraft) {
        SreUiStyle.registerLoadingBackground();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();
        if (startMillis < 0L) {
            startMillis = now;
        }

        // 先交接再绘制背景，避免加载完成后仍解码/上传一帧视频并强制等待淡出。
        if (!finished && reload.isDone()) {
            finished = true;
            Optional<Throwable> failure;
            try {
                reload.checkExceptions();
                failure = Optional.empty();
            } catch (Throwable t) {
                failure = Optional.of(t);
            }
            onFinish.accept(failure);
            // 失败回调可能已经安装新的恢复加载层，不能把它清掉。
            if (minecraft.getOverlay() == this) {
                minecraft.setOverlay(null);
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft, w, h);
            }
            return;
        }
        float alpha = fadeIn ? LoadingFx.smoothstep((now - startMillis) / (float) ENTER_MS) : 1.0F;

        SreUiStyle.renderLoadingBackdrop(g, w, h, partialTick, alpha);

        float target = LoadingFx.clamp01(reload.getActualProgress());
        displayProgress += (target - displayProgress) * 0.10F;
        if (target - displayProgress < 0.002F) {
            displayProgress = target;
        }

        boolean ready = false;
        drawProgress(g, w, h, alpha, ready);
        drawTip(g, w, h, alpha, now, ready);

    }

    private void drawProgress(GuiGraphics g, int w, int h, float alpha, boolean ready) {
        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 74;
        LoadingFx.drawRail(g, cx - half, cx + half, railY, displayProgress, alpha);

        String percent = (int) (displayProgress * 100) + "%";
        int pColor = LoadingFx.withAlpha(ready ? 0xD4AF37 : 0xFFF4DC, alpha);
        g.drawString(minecraft.font, percent,
                cx - minecraft.font.width(percent) / 2, railY - 16, pColor, false);
    }

    private void drawTip(GuiGraphics g, int w, int h, float alpha, long now, boolean ready) {
        int cx = w / 2;
        int y = h - 48;

        if (ready) {
            float pulse = 0.65F + 0.35F * (float) Math.sin(now / 180.0);
            String depart = resolveReadyText();
            int c = LoadingFx.withAlpha(0xD4AF37, alpha * pulse);
            g.drawString(minecraft.font, depart,
                    cx - minecraft.font.width(depart) / 2, y, c, false);
            return;
        }

        if (now - tipChangedAt > TIP_INTERVAL_MS) {
            prevTipIndex = tipIndex;
            tipIndex = (tipIndex + 1) % TIPS.size();
            tipChangedAt = now;
        }

        float fade = LoadingFx.smoothstep((now - tipChangedAt) / (float) TIP_FADE_MS);
        if (fade < 1.0F && prevTipIndex != tipIndex) {
            drawTipLine(g, resolveTip(prevTipIndex), cx, y - (int) (fade * 6.0F),
                    alpha * (1.0F - fade) * 0.85F);
        }
        drawTipLine(g, resolveTip(tipIndex), cx, y + (int) ((1.0F - fade) * 6.0F),
                alpha * fade * 0.85F);
    }

    private String resolveTip(int index) {
        String key = "loading.tip.starrailexpress." + (index + 1);
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        return TIPS.get(index);
    }

    private String resolveReadyText() {
        String key = "loading.ready";
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        return "Ready to depart";
    }

    private void drawTipLine(GuiGraphics g, String text, int cx, int y, float a) {
        if (a <= 0.01F) {
            return;
        }
        g.drawString(minecraft.font, text,
                cx - minecraft.font.width(text) / 2, y,
                LoadingFx.withAlpha(0x9E8B6E, a), false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    public static StarRailLoadingOverlay newInstance(Minecraft mc, ReloadInstance ri,
                                                     Consumer<Optional<Throwable>> handler, boolean fadeIn) {
        return new StarRailLoadingOverlay(mc, ri, handler, fadeIn);
    }
}
