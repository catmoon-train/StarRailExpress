package net.exmo.sre.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.BooleanSupplier;

/**
 * 星穹铁道风格 —— 加入世界加载界面
 * <p>
 * 背景使用帧序列动画播放，告别臃肿的粒子系统。
 * 仅保留：帧动画背景 + 暗色遮罩 + 状态文字 + 淡入淡出。
 */
@Environment(EnvType.CLIENT)
public class SREReceivingLevelScreen extends ReceivingLevelScreen {

    // ============================================================
    // 动画常量
    // ============================================================
    private static final long FADE_IN_MS = 800;
    private static final long FADE_OUT_MS = 600;
    private static final float VIDEO_FPS = 20.0F;
    private static final float BG_ALPHA = 0.55F; // 背景透明度，保证文字可读

    // ============================================================
    // 帧动画
    // ============================================================
    private static final FrameAnimationRenderer animRenderer = new FrameAnimationRenderer(VIDEO_FPS);

    // ============================================================
    // 状态
    // ============================================================
    private final BooleanSupplier levelReceived;
    private final long createdAt;

    private boolean fadingOut;
    private long fadeOutStart;
    private float fadeInProgress;
    private float fadeOutProgress;

    private int ellipsisState;
    private long lastEllipsisUpdate;

    // ============================================================
    // 构造
    // ============================================================

    public SREReceivingLevelScreen(BooleanSupplier levelReceived, Reason reason) {
        super(levelReceived, reason);
        this.levelReceived = levelReceived;
        this.createdAt = Util.getMillis();
    }

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    protected void init() {
        FrameAnimationRenderer.setInWorld(false);
        if (!animRenderer.hasFrames()) {
            animRenderer.loadFrames();
        }
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
    public void tick() {
        long now = Util.getMillis();

        // ── 渐出 ──────────────────────────────────────────────
        if (!fadingOut && levelReceived.getAsBoolean()) {
            fadingOut = true;
            fadeOutStart = now;
        }
        if (fadingOut) {
            fadeOutProgress = (now - fadeOutStart) / (float) FADE_OUT_MS;
            if (fadeOutProgress >= 1.0F) {
                onClose();
                return;
            }
        }

        // ── 渐入 ──────────────────────────────────────────────
        fadeInProgress = Mth.clamp((now - createdAt) / (float) FADE_IN_MS, 0.0F, 1.0F);

        // ── 省略号 ────────────────────────────────────────────
        if (now - lastEllipsisUpdate > 400) {
            ellipsisState = (ellipsisState + 1) % 4;
            lastEllipsisUpdate = now;
        }
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        float alpha = fadeInProgress;
        if (fadingOut) {
            alpha = 1.0F - Mth.clamp(fadeOutProgress, 0.0F, 1.0F);
        }

        // ── 纯黑底色 ──────────────────────────────────────────
        g.fill(0, 0, width, height, 0xFF000000);

        // ── 帧动画背景 ────────────────────────────────────────
        if (animRenderer.hasFrames()) {
            animRenderer.render(g, width, height, delta, alpha * BG_ALPHA);
        }

        // ── 暗色渐变遮罩（保证文字清晰） ──────────────────────
        int topAlpha = (int) (alpha * 100);
        int midAlpha = (int) (alpha * 160);
        g.fillGradient(0, 0, width, height / 2, topAlpha << 24, midAlpha << 24);
        g.fillGradient(0, height / 2, width, height, midAlpha << 24, ((int) (alpha * 60)) << 24);

        // ── 状态文字 ──────────────────────────────────────────
        renderStatus(g, alpha);

        // ── 黑幕遮罩（渐入/渐出） ────────────────────────────
        if (fadeInProgress < 1.0F) {
            g.fill(0, 0, width, height, (int) ((1.0F - fadeInProgress) * 255) << 24);
        }
        if (fadingOut) {
            g.fill(0, 0, width, height, (int) (Mth.clamp(fadeOutProgress, 0.0F, 1.0F) * 255) << 24);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 由 render() 统一绘制，跳过父类默认背景
        g.fill(0, 0, width, height, 0xFF000000);
    }

    // ============================================================
    // 子渲染方法
    // ============================================================

    /** 渲染状态文字和三连脉冲指示器 */
    private void renderStatus(GuiGraphics g, float alpha) {
        int cx = width / 2;
        int baseY = height / 2 + 30;

        // 状态文本 (含省略号)
        String text = Component.translatable("connect.joining").getString()
                + ".".repeat(ellipsisState);
        int textAlpha = (int) (alpha * 220);
        g.drawString(font, text,
                cx - font.width(text) / 2, baseY,
                (textAlpha << 24) | 0xDDE6F5);

        // 三连脉冲指示器
        int dotY = baseY + font.lineHeight + 14;
        long t = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            float pulse = (Mth.sin(t / 400.0F + i * 1.2F) + 1.0F) / 2.0F;
            int da = (int) (alpha * (60 + pulse * 120));
            int size = 2 + (int) (pulse * 2);
            g.fill(cx - 14 + i * 14 - size, dotY - size,
                    cx - 14 + i * 14 + size, dotY + size,
                    (da << 24) | 0x88CCFF);
        }
    }
}
