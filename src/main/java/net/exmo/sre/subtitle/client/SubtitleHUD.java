package net.exmo.sre.subtitle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * COD 风格字幕报幕 HUD 渲染器。
 * 
 * 特点：
 * - 队列化字幕，按顺序逐个播放
 * - 渐入 → 保持 → 渐出 动画
 * - 可选打字机动效
 * - 半透明背景底衬
 * - 主标题 + 副标题双层显示
 * - 支持 CENTER（屏幕中央，COD 风格）、TOP（屏幕顶部，兼容 broadcast）和 BOTTOM（屏幕底部）
 * 
 * 用法：
 * 在 HudRenderCallback 中调用 {@link #render(GuiGraphics, float)}
 * 在 ClientTickEvents 中调用 {@link #tick()}
 * 通过 {@link #enqueue(SubtitleEntry)} 添加字幕
 */
public class SubtitleHUD {

    public static final SubtitleHUD INSTANCE = new SubtitleHUD();

    // ---- 动画参数 ----
    private static final int DEFAULT_FADE_IN_TICKS  = 12;
    private static final int DEFAULT_FADE_OUT_TICKS = 18;
    private static final int TYPEWRITER_SPEED       = 2;

    private static final int PANEL_TOP = 0xC0101218;
    private static final int PANEL_BOTTOM = 0xA006080D;
    private static final int PANEL_BORDER = 0x55FFFFFF;
    private static final int TEXT_SECONDARY = 0xFFC9D2DD;
    private static final int CENTER_PADDING_X = 30;
    private static final int CENTER_PADDING_Y = 12;
    private static final int COMPACT_PADDING_X = 18;
    private static final int COMPACT_PADDING_Y = 7;

    // TOP 模式：与 BroadcasterHud 兼容（broadcast 从 y=20 开始渲染）
    // 将 TOP 模式字幕放在 y=55 处，避免与 broadcast 消息重叠
    private static final int TOP_MODE_Y = 56;
    private static final int BOTTOM_MODE_MARGIN = 74;

    // ---- 队列 ----
    private final Deque<SubtitleEntry> queue = new ArrayDeque<>();
    private SubtitleEntry current;

    // ---- 当前字幕动画状态 ----
    private int tick;
    private int visibleChars;

    private SubtitleHUD() {}

    // ==================== 公共 API ====================

    public void enqueue(SubtitleEntry entry) {
        queue.addLast(entry);
    }

    public void enqueueFromPacket(Component mainText, Component subText, int durationTicks,
                                   int color, boolean typewriter, int screenPosition) {
        enqueue(new SubtitleEntry(mainText, subText, durationTicks, DEFAULT_FADE_IN_TICKS,
                DEFAULT_FADE_OUT_TICKS, color, typewriter, screenPosition));
    }

    public void clear() {
        queue.clear();
        current = null;
        tick = 0;
        visibleChars = 0;
    }

    public void tick() {
        if (current == null) {
            if (!queue.isEmpty()) {
                current = queue.pollFirst();
                tick = 0;
                visibleChars = 0;
            }
            return;
        }

        tick++;

        if (current.typewriter && current.mainText != null) {
            String fullText = current.mainText.getString();
            if (visibleChars < fullText.length()) {
                if (tick % TYPEWRITER_SPEED == 0) {
                    visibleChars++;
                }
            }
        }

        int totalTicks = current.fadeInTicks + current.durationTicks + current.fadeOutTicks;
        if (tick >= totalTicks) {
            current = null;
            tick = 0;
            visibleChars = 0;
        }
    }

    /**
     * HUD 渲染（放在 HudRenderCallback 中）。
     */
    public void render(GuiGraphics guiGraphics, float partialTicks) {
        if (current == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;

        Font font = mc.font;
        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        boolean isTopMode    = current.screenPosition == SubtitleEntry.POS_TOP;
        boolean isBottomMode = current.screenPosition == SubtitleEntry.POS_BOTTOM;
        boolean isSmallFont  = isTopMode || isBottomMode;

        // TOP / BOTTOM 模式更像轻量提示；CENTER 保留报幕感。
        float mainScale = isSmallFont ? 1.15f : 2.0f;
        float subScale  = isSmallFont ? 0.82f : 1.0f;

        float age = tick + Mth.clamp(partialTicks, 0.0f, 1.0f);
        float alpha = computeAlpha(age);
        if (alpha <= 0.01f) return;
        float intro = easeOutCubic(Mth.clamp(age / current.fadeInTicks, 0.0f, 1.0f));
        float totalTicks = current.fadeInTicks + current.durationTicks + current.fadeOutTicks;
        float outro = easeInCubic(Mth.clamp((totalTicks - age) / current.fadeOutTicks, 0.0f, 1.0f));
        float settle = Math.min(intro, outro);

        Component mainDisplayText = getMainDisplayText();
        Component subDisplayText = current.subText;

        if ((mainDisplayText == null || mainDisplayText.getString().isEmpty())
                && (subDisplayText == null || subDisplayText.getString().isEmpty())) {
            return;
        }

        int textColor = normalizeColor(current.color != 0 ? current.color : 0xFFFFFFFF);
        int mainColor = applyAlpha(textColor, alpha);
        int subColor = current.color != 0
                ? applyAlpha(mixColor(textColor, TEXT_SECONDARY, 0.42f), alpha * 0.86f)
                : applyAlpha(TEXT_SECONDARY, alpha * 0.9f);

        boolean hasMain = mainDisplayText != null && !mainDisplayText.getString().isEmpty();
        boolean hasSub  = subDisplayText != null && !subDisplayText.getString().isEmpty();

        int mainWidth  = hasMain ? (int)(font.width(mainDisplayText) * mainScale) : 0;
        int subWidth   = hasSub  ? (int)(font.width(subDisplayText) * subScale) : 0;
        int maxWidth   = Math.max(mainWidth, subWidth);
        int maxContentWidth = screenWidth - (isSmallFont ? 56 : 88);
        if (maxWidth > maxContentWidth && maxContentWidth > 0) {
            float widthScale = maxContentWidth / (float) maxWidth;
            mainScale *= widthScale;
            subScale *= widthScale;
            mainWidth = hasMain ? (int)(font.width(mainDisplayText) * mainScale) : 0;
            subWidth = hasSub ? (int)(font.width(subDisplayText) * subScale) : 0;
            maxWidth = Math.max(mainWidth, subWidth);
        }

        float mainHeight = hasMain ? (9f * mainScale) : 0;
        float subHeight  = hasSub  ? (9f * subScale) : 0;
        float gap        = hasMain && hasSub ? (isSmallFont ? 4f : 7f) : 0;
        float totalHeight = mainHeight + gap + subHeight;
        int paddingX = isSmallFont ? COMPACT_PADDING_X : CENTER_PADDING_X;
        int paddingY = isSmallFont ? COMPACT_PADDING_Y : CENTER_PADDING_Y;

        // 计算 Y 基准位置
        float centerX = screenWidth / 2f;
        float baseY;
        if (isTopMode) {
            // TOP 模式：固定在屏幕顶部，y=TOP_MODE_Y 是背景框顶部
            baseY = TOP_MODE_Y;
        } else if (isBottomMode) {
            // BOTTOM 模式：固定在屏幕底部
            baseY = screenHeight - BOTTOM_MODE_MARGIN - totalHeight;
        } else {
            // CENTER 模式：屏幕中央偏上
            baseY = screenHeight * 0.44f - totalHeight / 2f;
        }

        float slideIn = isTopMode ? -12.0f : isBottomMode ? 12.0f : 10.0f;
        float slideOut = isTopMode ? -6.0f : isBottomMode ? 6.0f : -8.0f;
        float slide = (1.0f - intro) * slideIn + (1.0f - outro) * slideOut;
        float panelScale = isSmallFont ? 1.0f : 0.96f + 0.04f * settle;

        int bgW = Math.max(36, maxWidth + paddingX * 2);
        int bgH = Math.max(18, Mth.ceil(totalHeight + paddingY * 2));
        int bgX = -bgW / 2;
        int bgY = -paddingY;
        int accent = applyAlpha(textColor, alpha * (isSmallFont ? 0.42f : 0.55f));

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, baseY + slide, 0);
        pose.scale(panelScale, panelScale, 1f);

        drawPanel(guiGraphics, bgX, bgY, bgW, bgH, alpha, accent, isSmallFont);

        if (hasMain) {
            pose.pushPose();
            pose.translate(0, 0, 0);
            pose.scale(mainScale, mainScale, 1f);
            guiGraphics.drawString(font, mainDisplayText, -font.width(mainDisplayText) / 2, 0, mainColor, true);
            pose.popPose();
        }

        if (hasSub) {
            pose.pushPose();
            pose.translate(0, mainHeight + gap, 0);
            pose.scale(subScale, subScale, 1f);
            guiGraphics.drawString(font, subDisplayText, -font.width(subDisplayText) / 2, 0, subColor, true);
            pose.popPose();
        }

        pose.popPose();
    }

    // ==================== 内部方法 ====================

    private float computeAlpha(float age) {
        int fadeIn  = current.fadeInTicks;
        int dur     = current.durationTicks;
        int fadeOut = current.fadeOutTicks;

        if (age < fadeIn) {
            return easeOutCubic(Mth.clamp(age / fadeIn, 0f, 1f));
        } else if (age < fadeIn + dur) {
            return 1f;
        } else {
            float exitTick = age - fadeIn - dur;
            return easeInCubic(Mth.clamp(1f - exitTick / fadeOut, 0f, 1f));
        }
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h,
                           float alpha, int accent, boolean compact) {
        int shadowAlpha = compact ? 56 : 72;
        guiGraphics.fill(x + 2, y + 3, x + w + 2, y + h + 3, (int)(shadowAlpha * alpha) << 24);
        guiGraphics.fillGradient(x, y, x + w, y + h,
                applyAlpha(PANEL_TOP, alpha * (compact ? 0.64f : 0.74f)),
                applyAlpha(PANEL_BOTTOM, alpha * (compact ? 0.56f : 0.68f)));
        guiGraphics.renderOutline(x, y, w, h, applyAlpha(PANEL_BORDER, alpha * 0.45f));
        guiGraphics.fill(x + 2, y + 1, x + w - 2, y + 2, applyAlpha(0x55FFFFFF, alpha * 0.45f));
        if (compact) {
            guiGraphics.fill(x + 1, y + 2, x + 3, y + h - 2, accent);
        } else {
            guiGraphics.fill(x + 12, y + h - 2, x + w - 12, y + h - 1, accent);
        }
    }

    private Component getMainDisplayText() {
        if (current.mainText == null) return Component.empty();
        if (!current.typewriter) return current.mainText;

        String full = current.mainText.getString();
        int len = Math.min(visibleChars, full.length());
        boolean cursor = len < full.length() && ((tick / 6) % 2 == 0);
        return Component.literal(full.substring(0, len) + (cursor ? "|" : ""));
    }

    private static int applyAlpha(int color, float alpha) {
        color = normalizeColor(color);
        int a = Mth.clamp((int)((color >> 24 & 0xFF) * alpha), 0, 255);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int normalizeColor(int color) {
        if (color == 0) return 0xFFFFFFFF;
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    private static int mixColor(int a, int b, float t) {
        a = normalizeColor(a);
        b = normalizeColor(b);
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Mth.clamp((int) Mth.lerp(t, ar, br), 0, 255);
        int g = Mth.clamp((int) Mth.lerp(t, ag, bg), 0, 255);
        int blue = Mth.clamp((int) Mth.lerp(t, ab, bb), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | blue;
    }

    private static float easeOutCubic(float t) {
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    // ==================== 字幕数据类 ====================

    /**
     * 单条字幕条目。
     */
    public static class SubtitleEntry {
        public static final int POS_CENTER = 0;
        public static final int POS_TOP    = 1;
        public static final int POS_BOTTOM = 2;

        public final Component mainText;
        public final Component subText;
        public final int       durationTicks;
        public final int       fadeInTicks;
        public final int       fadeOutTicks;
        public final int       color;
        public final boolean   typewriter;
        public final int       screenPosition;

        public SubtitleEntry(Component mainText, Component subText,
                             int durationTicks, int fadeInTicks, int fadeOutTicks,
                             int color, boolean typewriter, int screenPosition) {
            this.mainText       = mainText;
            this.subText        = subText != null ? subText : Component.empty();
            this.durationTicks  = Math.max(durationTicks, 20);
            this.fadeInTicks    = Math.max(fadeInTicks, 1);
            this.fadeOutTicks   = Math.max(fadeOutTicks, 1);
            this.color          = color;
            this.typewriter     = typewriter;
            this.screenPosition = screenPosition;
        }
    }
}
