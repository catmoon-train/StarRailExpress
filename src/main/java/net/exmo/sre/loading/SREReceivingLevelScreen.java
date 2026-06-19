package net.exmo.sre.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * 星穹铁道风格 —— 进入世界（接收区块）加载界面。
 * <p>
 * 背景全幅播放列车视频（帧序列），叠加暗角、星轨指示器与状态文字。
 * 时间线与资源加载界面 {@link StarRailLoadingOverlay} 一致：
 * 黑屏淡入 → 不确定加载（彗星往复）→ 收到世界后保留 1~2 秒并把星轨“走满”
 * （此时后台仍在加载区块）→ 缓动淡出回黑再关闭。
 * <p>
 * 关键修复：不再调用 {@code super.render}（其会重绘被覆写成纯黑的背景，
 * 把视频盖掉，导致旧版“只看得到文字”），改为完全自绘。
 */
@Environment(EnvType.CLIENT)
public class SREReceivingLevelScreen extends ReceivingLevelScreen {

    // ── 时间线（毫秒） ────────────────────────────────────────
    private static final long ENTER_MS = 650;     // 黑屏淡入
    private static final long MIN_SHOW_MS = 900;   // 最短展示，避免秒进时一闪而过
    private static final long END_HOLD_MS = 1300;  // 收到世界后保留（让区块收尾）
    private static final long EXIT_MS = 650;       // 淡出回黑

    private static final float VIDEO_FPS = 20.0F;
    private static final FrameAnimationRenderer ANIM = new FrameAnimationRenderer(VIDEO_FPS);

    private final BooleanSupplier levelReceived;
    private final long createdAt;

    private long receivedAt = -1L;   // 满足展示时长且收到世界的时刻
    private int ellipsis;
    private long lastEllipsisAt;

    public SREReceivingLevelScreen(BooleanSupplier levelReceived, Reason reason) {
        super(levelReceived, reason);
        this.levelReceived = levelReceived;
        this.createdAt = Util.getMillis();
    }

    // ── 生命周期 ──────────────────────────────────────────────

    @Override
    protected void init() {
        FrameAnimationRenderer.setInWorld(false);
        if (!ANIM.hasFrames()) {
            ANIM.loadFrames();
        }
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
    public void tick() {
        long now = Util.getMillis();
        if (now - lastEllipsisAt > 400L) {
            ellipsis = (ellipsis + 1) % 4;
            lastEllipsisAt = now;
        }
    }

    // ── 渲染 ──────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int w = this.width;
        int h = this.height;
        long now = Util.getMillis();

        // 进入阶段透明度
        float enterAlpha = LoadingFx.smoothstep((now - createdAt) / (float) ENTER_MS);

        // 满足最短展示时长且世界已就绪 → 进入收尾
        if (receivedAt < 0L && levelReceived.getAsBoolean() && now - createdAt >= MIN_SHOW_MS) {
            receivedAt = now;
        }

        float exitAlpha = 1.0F;
        float arriveT = 0.0F;
        boolean arriving = receivedAt >= 0L;
        if (arriving) {
            long since = now - receivedAt;
            arriveT = LoadingFx.smoothstep(since / (float) END_HOLD_MS);
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

        // ── 背景：黑底 + 列车视频 ─────────────────────────────
        g.fill(0, 0, w, h, 0xFF000000);
        if (ANIM.hasFrames()) {
            ANIM.render(g, w, h, delta, alpha);
        }
        LoadingFx.drawVignette(g, w, h, alpha);

        // ── 标题 ─────────────────────────────────────────────
        String title = Component.translatable("screen.starrailexpress.loading.title").getString();
        LoadingFx.drawCenteredScaled(g, font, title,
                w / 2, (int) (h * 0.30F), 2.0F, LoadingFx.withAlpha(0xEAF4FF, alpha));

        // ── 星轨指示器：加载中彗星往复；收尾时走满 ───────────
        int half = Math.min(w / 3, 320);
        int cx = w / 2;
        int railY = h - 70;
        if (arriving) {
            LoadingFx.drawRail(g, cx - half, cx + half, railY, arriveT, alpha);
        } else {
            float phase = (now % 2600L) / 2600.0F;
            LoadingFx.drawComet(g, cx - half, cx + half, railY, phase, alpha);
        }

        // ── 状态文字 ─────────────────────────────────────────
        String base = Component.translatable("loading.world.generating").getString();
        String text = arriving ? base : base + ".".repeat(ellipsis);
        int color = LoadingFx.withAlpha(arriving ? 0xCFF3FF : 0xC8D6EA, alpha);
        g.drawString(font, text, cx - font.width(text) / 2, railY - 16, color, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 背景统一在 render() 中绘制，跳过父类默认背景。
        g.fill(0, 0, width, height, 0xFF000000);
    }
}
