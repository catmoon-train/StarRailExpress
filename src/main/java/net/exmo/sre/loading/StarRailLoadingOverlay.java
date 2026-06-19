package net.exmo.sre.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.loading.texture.ConfigTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 星穹铁道风格的资源加载覆盖层（替换原版 Mojang Logo / 资源重载界面）。
 * <p>
 * 背景播放列车视频（帧序列），叠加暗角、星轨进度条、标题与轮换提示。
 * 整条时间线为：黑屏淡入 → 加载 → 进度满后停留 → 淡出回黑，
 * 各阶段全部使用 smoothstep 缓动，衔接平顺且与“进入世界”界面观感一致。
 */
@Environment(EnvType.CLIENT)
public class StarRailLoadingOverlay extends Overlay {

    // ── 时间线（毫秒） ────────────────────────────────────────
    private static final long ENTER_MS = 650;          // 黑屏淡入
    private static final long COMPLETE_HOLD_MS = 950;   // 进度满后停留（让区块/资源收尾）
    private static final long EXIT_MS = 700;            // 淡出回黑
    private static final long TIP_INTERVAL_MS = 4200;   // 提示轮换间隔
    private static final long TIP_FADE_MS = 320;        // 提示交叉淡入淡出

    private static final float VIDEO_FPS = 20.0F;

    /** 列车视频背景；静态共享，跨重载复用已注册的帧。 */
    private static final FrameAnimationRenderer ANIM = new FrameAnimationRenderer(VIDEO_FPS);

    /** 无视频帧时回退使用的静态背景图。 */
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "background.png");

    private static final String TITLE = "STAR RAIL EXPRESS";

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
    private long completeMillis = -1L;   // reload 完成并已通知 onFinish 的时刻
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

        FrameAnimationRenderer.setInWorld(false);
//        if (!ANIM.hasFrames()) {
//            ANIM.loadFrames();
//        }
        ANIM.reset();
    }

    public static void registerTextures(Minecraft minecraft) {
        minecraft.getTextureManager().register(BG_TEXTURE, new ConfigTexture(BG_TEXTURE));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        long now = Util.getMillis();
        if (startMillis < 0L) startMillis = now;

        // ── 阶段透明度 ───────────────────────────────────────
        float enterAlpha = fadeIn ? LoadingFx.smoothstep((now - startMillis) / (float) ENTER_MS) : 1.0F;
        float exitAlpha = 1.0F;
        if (completeMillis >= 0L) {
            long exitElapsed = now - (completeMillis + COMPLETE_HOLD_MS);
            if (exitElapsed >= 0L) {
                exitAlpha = 1.0F - LoadingFx.smoothstep(exitElapsed / (float) EXIT_MS);
                if (exitElapsed >= EXIT_MS) {
                    this.minecraft.setOverlay(null);
                    return;
                }
            }
        }
        float alpha = enterAlpha * exitAlpha;

        // ── 背景：黑底 + 列车视频（无帧则回退静图） ──────────
        g.fill(0, 0, w, h, 0xFF000000);
        if (ANIM.hasFrames()) {
            ANIM.render(g, w, h, partialTick, alpha);
        } else {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0F, 1.0F, 1.0F, alpha);
            g.blit(BG_TEXTURE, 0, 0, 0, 0, w, h, w, h);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        LoadingFx.drawVignette(g, w, h, alpha);

        // ── 进度（平滑逼近真实进度；完成阶段强制走满） ──────
        float target = completeMillis >= 0L ? 1.0F
                : LoadingFx.clamp01(reload.getActualProgress());
        displayProgress += (target - displayProgress) * 0.10F;
        if (target - displayProgress < 0.002F) displayProgress = target;

        // ── 文字与进度区 ─────────────────────────────────────
//        LoadingFx.drawCenteredScaled(g, minecraft.font, TITLE,
//                w / 2, (int) (h * 0.30F), 2.0F, LoadingFx.withAlpha(0xEAF4FF, alpha));

        boolean ready = completeMillis >= 0L && displayProgress > 0.999F;
        drawProgress(g, w, h, alpha, ready);
        drawTip(g, w, h, alpha, now, ready);

        // ── 检测加载完成：通知 onFinish 并切换底层屏幕（仅一次） ──
        if (!finished && reload.isDone()) {
            finished = true;
            completeMillis = now;
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                onFinish.accept(Optional.of(t));
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(minecraft, w, h);
            }
        }
    }

    /** 星轨进度条 + 居中百分比。 */
    private void drawProgress(GuiGraphics g, int w, int h, float alpha, boolean ready) {
        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 74;

        LoadingFx.drawRail(g, cx - half, cx + half, railY, displayProgress, alpha);

        String percent = (int) (displayProgress * 100) + "%";
        int pColor = LoadingFx.withAlpha(ready ? 0xCFF3FF : 0xEAF4FF, alpha);
        g.drawString(minecraft.font, percent,
                cx - minecraft.font.width(percent) / 2, railY - 16, pColor, true);
    }

    /** 轮换提示（交叉淡入淡出）；完成阶段显示“准备出发”。 */
    private void drawTip(GuiGraphics g, int w, int h, float alpha, long now, boolean ready) {
        int cx = w / 2;
        int y = h - 48;

        if (ready) {
            String depart = "Ready to depart";
            int c = LoadingFx.withAlpha(0xBFE4FF, alpha);
            g.drawString(minecraft.font, depart,
                    cx - minecraft.font.width(depart) / 2, y, c, true);
            return;
        }

        if (now - tipChangedAt > TIP_INTERVAL_MS) {
            prevTipIndex = tipIndex;
            tipIndex = (tipIndex + 1) % TIPS.size();
            tipChangedAt = now;
        }

        float fade = LoadingFx.smoothstep((now - tipChangedAt) / (float) TIP_FADE_MS);
        // 旧提示上浮淡出
        if (fade < 1.0F && prevTipIndex != tipIndex) {
            drawTipLine(g, TIPS.get(prevTipIndex), cx, y - (int) (fade * 6.0F),
                    alpha * (1.0F - fade) * 0.85F);
        }
        // 新提示下沉淡入
        drawTipLine(g, TIPS.get(tipIndex), cx, y + (int) ((1.0F - fade) * 6.0F),
                alpha * fade * 0.85F);
    }

    private void drawTipLine(GuiGraphics g, String text, int cx, int y, float a) {
        if (a <= 0.01F) return;
        g.drawString(minecraft.font, text,
                cx - minecraft.font.width(text) / 2, y,
                LoadingFx.withAlpha(0xB8C6DA, a), true);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** 工厂方法，供 Mod 加载器 / Mixin 调用。 */
    public static StarRailLoadingOverlay newInstance(Minecraft mc, ReloadInstance ri,
                                                     Consumer<Optional<Throwable>> handler, boolean fadeIn) {
        return new StarRailLoadingOverlay(mc, ri, handler, fadeIn);
    }
}
