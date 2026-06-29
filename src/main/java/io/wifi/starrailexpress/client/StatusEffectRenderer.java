package io.wifi.starrailexpress.client;

import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.client.hud.CommonClientHudRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 火车 HUD 的药水效果渲染：纵向卡片排版，常态仅显示图标 + 剩余时间（紧凑、不占空间），
 * 按住 Shift 时展开显示效果名称（含等级、自动换行）。
 *
 * <p>所有绘制走 {@link GuiGraphics} → {@link OptimizedTextRenderer} 的批处理通道，
 * 并以 tick 脏标记为门控，仅在 tick 变化时重建批次，其余帧复用缓存，避免每帧重复构建文本。
 */
public final class StatusEffectRenderer {
    private StatusEffectRenderer() {
    }

    // ── 排版参数 ────────────────────────────────────────────────────────────────
    private static final int ICON = 18;
    private static final int ACCENT_W = 1;   // 左侧类别色条（细）
    private static final int PAD_X = 4;       // 水平内边距
    private static final int PAD_Y = 3;       // 垂直内边距（展开态）
    private static final int GAP = 5;         // 图标与文字间距
    private static final int ROW_GAP = 3;     // 卡片间距
    private static final int NAME_MAX_W = 110; // 名称换行宽度上限
    private static final int MARGIN = 8;      // 距屏幕右/上边距

    // ── 配色（沿用游戏 HUD 的半透明黑底 + 柔和强调色） ───────────────────────────────
    private static final int BG = 0x66000000;                  // 更淡的半透明黑底
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int TIME_COLOR = 0xFFBFC4CC;
    private static final int TIME_URGENT = 0xFFFF6B6B;          // 剩余 < 5s
    private static final int ACCENT_GOOD = 0xFF44BB66;
    private static final int ACCENT_BAD = 0xFFFF6B6B;
    private static final int ACCENT_NEUTRAL = 0xFF22BBCC;

    /**
     * 渲染玩家当前的状态效果列表。
     *
     * @return true 表示已接管（无需再调用原版 renderEffects）。
     */
    public static boolean render(Minecraft mc, GuiGraphics realGraphics, LocalPlayer player) {
        // 打开界面 / 隐藏 HUD 时不绘制（与原版行为及 StatusBarHUD 一致）
        if (mc.options.hideGui || mc.screen != null) {
            return true;
        }

        Collection<MobEffectInstance> active = player.getActiveEffects();
        if (active.isEmpty()) {
            return true;
        }

        List<MobEffectInstance> effects = new ArrayList<>(active.size());
        for (MobEffectInstance e : active) {
            if (e.showIcon()) {
                effects.add(e);
            }
        }
        if (effects.isEmpty()) {
            return true;
        }

        // 有益 → 中性 → 有害，同类按剩余时间从长到短
        effects.sort(Comparator
                .comparingInt(StatusEffectRenderer::categoryOrder)
                .thenComparing(Comparator
                        .comparingLong(StatusEffectRenderer::remainingTicks)
                        .reversed()));

        final GuiGraphics g = realGraphics;
        final Font font = mc.font;
        final boolean expanded = Screen.hasShiftDown();
        final int screenW = realGraphics.guiWidth();
        final int top = CommonClientHudRenderer.effectStartY > 0
                ? CommonClientHudRenderer.effectStartY + 6
                : MARGIN;

        int y = top;
        for (MobEffectInstance effect : effects) {
            y = expanded
                    ? drawExpanded(g, font, mc, screenW, y, effect)
                    : drawCompact(g, font, mc, screenW, y, effect);
        }
        return true;
    }

    // ── 紧凑态：图标 + 剩余时间 ───────────────────────────────────────────────────
    private static int drawCompact(GuiGraphics g, Font font, Minecraft mc, int screenW, int y,
            MobEffectInstance effect) {
        String time = formatDuration(effect);
        int timeW = font.width(time);

        int contentW = ICON + GAP + timeW;
        int cardW = ACCENT_W + PAD_X + contentW + PAD_X;
        int cardH = ICON + 2;
        int cardX = screenW - MARGIN - cardW;

        drawFrame(g, cardX, y, cardW, cardH, accentColor(effect));

        int iconX = cardX + ACCENT_W + PAD_X;
        int iconY = y + (cardH - ICON) / 2;
        drawIcon(g, mc, effect, iconX, iconY);

        int textX = iconX + ICON + GAP;
        int textY = y + (cardH - font.lineHeight) / 2 + 1;
        g.drawString(font, time, textX, textY, timeColor(effect), false);

        return y + cardH + ROW_GAP;
    }

    // ── 展开态（Shift）：图标 + 名称（换行）+ 剩余时间 ──────────────────────────────
    private static int drawExpanded(GuiGraphics g, Font font, Minecraft mc, int screenW, int y,
            MobEffectInstance effect) {
        List<FormattedCharSequence> nameLines = font.split(displayName(effect), NAME_MAX_W);
        String time = formatDuration(effect);

        int textW = font.width(time);
        for (FormattedCharSequence line : nameLines) {
            textW = Math.max(textW, font.width(line));
        }
        textW = Math.min(textW, NAME_MAX_W);

        int contentW = ICON + GAP + textW;
        int cardW = ACCENT_W + PAD_X + contentW + PAD_X;
        int textBlockH = (nameLines.size() + 1) * font.lineHeight;
        int innerH = Math.max(ICON, textBlockH);
        int cardH = innerH + 2 * PAD_Y;
        int cardX = screenW - MARGIN - cardW;

        drawFrame(g, cardX, y, cardW, cardH, accentColor(effect));

        int iconX = cardX + ACCENT_W + PAD_X;
        int iconY = y + (cardH - ICON) / 2;
        drawIcon(g, mc, effect, iconX, iconY);

        int textX = iconX + ICON + GAP;
        int textY = y + (cardH - textBlockH) / 2;
        for (FormattedCharSequence line : nameLines) {
            g.drawString(font, line, textX, textY, NAME_COLOR, false);
            textY += font.lineHeight;
        }
        g.drawString(font, time, textX, textY, timeColor(effect), false);

        return y + cardH + ROW_GAP;
    }

    // ── 绘制辅助 ──────────────────────────────────────────────────────────────────
    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + ACCENT_W, y + h, accent);
    }

    private static void drawIcon(GuiGraphics g, Minecraft mc, MobEffectInstance effect, int x, int y) {
        TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect.getEffect());
        g.blit(x, y, 0, ICON, ICON, sprite);
    }

    private static MutableComponent displayName(MobEffectInstance effect) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        if (amplifier >= 1 && amplifier <= 9) {
            name = Component.translatable("potion.withAmplifier", name,
                    Component.translatable("potion.potency." + amplifier));
        }
        return name;
    }

    private static String formatDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "∞"; // ∞
        }
        int seconds = effect.getDuration() / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static int timeColor(MobEffectInstance effect) {
        if (!effect.isInfiniteDuration() && effect.getDuration() < 100) {
            return TIME_URGENT;
        }
        return TIME_COLOR;
    }

    private static int accentColor(MobEffectInstance effect) {
        return switch (effect.getEffect().value().getCategory()) {
            case BENEFICIAL -> ACCENT_GOOD;
            case HARMFUL -> ACCENT_BAD;
            default -> ACCENT_NEUTRAL;
        };
    }

    private static int categoryOrder(MobEffectInstance effect) {
        MobEffectCategory category = effect.getEffect().value().getCategory();
        return switch (category) {
            case BENEFICIAL -> 0;
            case NEUTRAL -> 1;
            case HARMFUL -> 2;
        };
    }

    private static long remainingTicks(MobEffectInstance effect) {
        return effect.isInfiniteDuration() ? Long.MAX_VALUE : effect.getDuration();
    }
}
