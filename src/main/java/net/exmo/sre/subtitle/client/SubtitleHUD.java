package net.exmo.sre.subtitle.client;

import net.minecraft.client.Minecraft;
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
    private static final int DEFAULT_FADE_IN_TICKS  = 15;
    private static final int DEFAULT_FADE_OUT_TICKS = 20;
    private static final int TYPEWRITER_SPEED       = 2;

    private static final int BG_COLOR = 0x80000000;
    private static final int BG_PADDING_X = 24;
    private static final int BG_PADDING_Y = 10;

    // TOP 模式：与 BroadcasterHud 兼容（broadcast 从 y=20 开始渲染）
    // 将 TOP 模式字幕放在 y=55 处，避免与 broadcast 消息重叠
    private static final int TOP_MODE_Y = 55;

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
        if (mc.options.hideGui && mc.screen != null) return;
        if (mc.player == null) return;

        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        boolean isTopMode    = current.screenPosition == SubtitleEntry.POS_TOP;
        boolean isBottomMode = current.screenPosition == SubtitleEntry.POS_BOTTOM;
        boolean isSmallFont  = isTopMode || isBottomMode;

        // TOP / BOTTOM 模式使用较小字体
        float mainScale = isSmallFont ? 1.5f : 2.2f;
        float subScale  = isSmallFont ? 0.9f : 1.1f;

        float alpha = computeAlpha();

        Component mainDisplayText = getMainDisplayText();
        Component subDisplayText = current.subText;

        if ((mainDisplayText == null || mainDisplayText.getString().isEmpty())
                && (subDisplayText == null || subDisplayText.getString().isEmpty())) {
            return;
        }

        int textColor = current.color != 0 ? current.color : 0xFFFFFFFF;
        int mainColor = applyAlpha(textColor, alpha);
        int subColor  = applyAlpha(textColor, alpha * 0.8f);
        int bgColor   = applyAlpha(BG_COLOR, alpha * 0.6f);

        boolean hasMain = mainDisplayText != null && !mainDisplayText.getString().isEmpty();
        boolean hasSub  = subDisplayText != null && !subDisplayText.getString().isEmpty();

        int mainWidth  = hasMain ? (int)(mc.font.width(mainDisplayText) * mainScale) : 0;
        int subWidth   = hasSub  ? (int)(mc.font.width(subDisplayText) * subScale) : 0;
        int maxWidth   = Math.max(mainWidth, subWidth);

        float mainHeight = hasMain ? (9f * mainScale) : 0;
        float subHeight  = hasSub  ? (9f * subScale) : 0;
        float gap        = hasMain && hasSub ? (isTopMode ? 4f : 8f) : 0;
        float totalHeight = mainHeight + gap + subHeight;

        // 计算 Y 基准位置
        float centerX = screenWidth / 2f;
        float baseY;
        if (isTopMode) {
            // TOP 模式：固定在屏幕顶部，y=TOP_MODE_Y 是背景框顶部
            baseY = TOP_MODE_Y + BG_PADDING_Y;
        } else if (isBottomMode) {
            // BOTTOM 模式：固定在屏幕底部
            baseY = screenHeight - totalHeight - BG_PADDING_Y * 2;
        } else {
            // CENTER 模式：屏幕中央偏上
            baseY = screenHeight * 0.55f - totalHeight / 2f;
        }

        // 背景框
        if (maxWidth > 0 && totalHeight > 0) {
            float bgX = (screenWidth - maxWidth) / 2f - BG_PADDING_X;
            float bgY = baseY - BG_PADDING_Y;
            float bgW = maxWidth + BG_PADDING_X * 2;
            float bgH = totalHeight + BG_PADDING_Y * 2;

            guiGraphics.fill(
                    (int) bgX, (int) bgY,
                    (int) (bgX + bgW), (int) (bgY + bgH),
                    bgColor);
        }

        // 渲染主标题
        if (hasMain) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(centerX, baseY, 0);
            pose.scale(mainScale, mainScale, 1f);
            guiGraphics.drawString(mc.font, mainDisplayText,
                    -mc.font.width(mainDisplayText) / 2, 0, mainColor, false);
            pose.popPose();
        }

        // 渲染副标题
        if (hasSub) {
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(centerX, baseY + mainHeight + gap, 0);
            pose.scale(subScale, subScale, 1f);
            guiGraphics.drawString(mc.font, subDisplayText,
                    -mc.font.width(subDisplayText) / 2, 0, subColor, false);
            pose.popPose();
        }
    }

    // ==================== 内部方法 ====================

    private float computeAlpha() {
        int fadeIn  = current.fadeInTicks;
        int dur     = current.durationTicks;
        int fadeOut = current.fadeOutTicks;

        if (tick < fadeIn) {
            return Mth.clamp((float) tick / fadeIn, 0f, 1f);
        } else if (tick < fadeIn + dur) {
            return 1f;
        } else {
            int exitTick = tick - fadeIn - dur;
            return Mth.clamp(1f - (float) exitTick / fadeOut, 0f, 1f);
        }
    }

    private Component getMainDisplayText() {
        if (current.mainText == null) return Component.empty();
        if (!current.typewriter) return current.mainText;

        String full = current.mainText.getString();
        int len = Math.min(visibleChars, full.length());
        return Component.literal(full.substring(0, len));
    }

    private static int applyAlpha(int color, float alpha) {
        int a = Mth.clamp((int)((color >> 24 & 0xFF) * alpha), 0, 255);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
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
