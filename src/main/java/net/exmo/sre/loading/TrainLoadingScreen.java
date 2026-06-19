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
 * 星穹铁道风格 —— 世界生成 / 区块加载界面（替换原版 LevelLoadingScreen）。
 * <p>
 * 与资源加载 {@link StarRailLoadingOverlay} 和进入世界 {@link SREReceivingLevelScreen}
 * 共用同一套视觉语言：全幅列车视频背景 + 暗角 + 星轨进度 + 轮换提示，
 * 时间线为「黑屏淡入 → 加载 → 收尾走满 → 缓动淡出」，三个界面无缝衔接。
 */
@Environment(EnvType.CLIENT)
public class TrainLoadingScreen extends Screen {

    // ── 时间线（毫秒） ────────────────────────────────────────
    private static final long ENTER_MS = 650;
    private static final long END_HOLD_MS = 700;   // 触发关闭后保留（走满星轨）
    private static final long EXIT_MS = 600;
    private static final long SAFETY_TIMEOUT_MS = 30_000L; // 卡死兜底
    private static final long NARRATION_DELAY_MS = 2000L;
    private static final long TIP_INTERVAL_MS = 4200L;
    private static final long TIP_FADE_MS = 320L;
    private static final long ELLIPSIS_INTERVAL_MS = 500L;

    private static final float VIDEO_FPS = 20.0F;
    private static final FrameAnimationRenderer ANIM = new FrameAnimationRenderer(VIDEO_FPS);

    // ── 依赖 ─────────────────────────────────────────────────
    private final StoringChunkProgressListener progressListener;
    private final boolean hasProgressListener;
    private final BooleanSupplier levelReceived;
    private final long createdAt;

    // ── 状态 ─────────────────────────────────────────────────
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

    // ── 生命周期 ──────────────────────────────────────────────

    @Override
    protected void init() {
        FrameAnimationRenderer.setInWorld(false);
//        if (!ANIM.hasFrames()) {
//            ANIM.loadFrames();
//        }
        ANIM.reset();
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

        // 触发收尾：收到世界 或 兜底超时
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

    // ── 叙述（无障碍） ────────────────────────────────────────

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

    // ── 渲染 ──────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        long now = Util.getMillis();

        float enterAlpha = LoadingFx.smoothstep((now - createdAt) / (float) ENTER_MS);
        float exitAlpha = 1.0F;
        float resolveT = 0.0F;       // 收尾时把星轨推满
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

        // 背景：黑底 + 列车视频
        g.fill(0, 0, w, h, 0xFF000000);
        if (ANIM.hasFrames()) {
            ANIM.render(g, w, h, delta, alpha);
        }
        LoadingFx.drawVignette(g, w, h, alpha);

        // 标题
        String title = this.title.getString();
        LoadingFx.drawCenteredScaled(g, font, title,
                w / 2, (int) (h * 0.30F), 2.0F, LoadingFx.withAlpha(0xEAF4FF, alpha));

        // 进度逼近真实值；收尾阶段走满
        float real = hasProgressListener ? LoadingFx.clamp01(progressListener.getProgress()) : 0.0F;
        float target = resolving ? Math.max(real, resolveT) : real;
        displayProgress += (target - displayProgress) * 0.12F;
        if (Math.abs(target - displayProgress) < 0.002F) displayProgress = target;

        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 70;

        if (hasProgressListener || resolving) {
            // 确定进度：星轨光带 + 居中百分比
            LoadingFx.drawRail(g, cx - half, cx + half, railY, displayProgress, alpha);
            String percent = (int) (displayProgress * 100) + "%";
            g.drawString(font, percent, cx - font.width(percent) / 2, railY - 16,
                    LoadingFx.withAlpha(0xEAF4FF, alpha), true);
        } else {
            // 不确定进度：彗星往复 + “进入世界…”
            float phase = (now % 2600L) / 2600.0F;
            LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, alpha);
            String text = Component.translatable("loading.world.generating").getString()
                    + ".".repeat(ellipsis);
            g.drawString(font, text, cx - font.width(text) / 2, railY - 16,
                    LoadingFx.withAlpha(0xC8D6EA, alpha), true);
        }

        // 轮换提示（交叉淡入淡出）
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
        if (a <= 0.01F) return;
        g.drawString(font, text, cx - font.width(text) / 2, y,
                LoadingFx.withAlpha(0xB8C6DA, a), true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 背景统一在 render() 中绘制。
        g.fill(0, 0, width, height, 0xFF000000);
    }
}
