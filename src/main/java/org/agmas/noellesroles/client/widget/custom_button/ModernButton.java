package org.agmas.noellesroles.client.widget.custom_button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 仿 RoleIntroduceScreen 风格的现代按钮，支持同时在多个方向显示色条。
 * 文字会自动居中于扣除色条（实色 + 光晕）后的剩余内容区域。
 *
 * <p>用法示例：
 * <pre>{@code
 * // 默认：上下左右四边都有色条，文字居中于四边内缩后区域
 * ModernButton btn = ModernButton.builder(label, onPress)
 *     .bounds(x, y, 120, 20)
 *     .build();
 *
 * // 仅左侧色条，文字向右偏移
 * ModernButton.builder(label, onPress)
 *     .bounds(x, y, 120, 20)
 *     .accentBar(ModernButton.AccentSide.LEFT)
 *     .build();
 *
 * // 左 + 顶，文字向右下偏移
 * ModernButton.builder(label, onPress)
 *     .bounds(x, y, 120, 20)
 *     .accentBar(ModernButton.AccentSide.LEFT, ModernButton.AccentSide.TOP)
 *     .build();
 *
 * // 不显示色条，文字完全居中
 * ModernButton.builder(label, onPress)
 *     .bounds(x, y, 120, 20)
 *     .accentBar()
 *     .build();
 * }</pre>
 */
public class ModernButton extends net.minecraft.client.gui.components.Button {

    // ══════════════════════════════════════════════════════════════════
    // 色条方向枚举
    // ══════════════════════════════════════════════════════════════════

    public enum AccentSide {
        LEFT, RIGHT, TOP, BOTTOM
    }

    /** 默认四边全显示 */
    private static final Set<AccentSide> DEFAULT_SIDES =
            Collections.unmodifiableSet(EnumSet.allOf(AccentSide.class));

    // ══════════════════════════════════════════════════════════════════
    // 字段
    // ══════════════════════════════════════════════════════════════════

    private static final int DEFAULT_ACCENT = 0xFF5577CC;
    private static final int BAR_THICKNESS  = 3;
    private static final int BAR_GLOW       = 4;
    /** 单侧色条（实色 + 光晕）占用的总像素宽/高 */
    private static final int BAR_INSET      = BAR_THICKNESS + BAR_GLOW;

    private final int            accentColor;
    private final Set<AccentSide> accentSides;
    private float hoverAnim = 0f;

    // ══════════════════════════════════════════════════════════════════
    // 构造（私有）
    // ══════════════════════════════════════════════════════════════════

    private ModernButton(int x, int y, int w, int h,
            Component label,
            OnPress onPress,
            CreateNarration narration,
            int accentColor,
            Set<AccentSide> accentSides) {
        super(x, y, w, h, label, onPress, narration);
        this.accentColor = accentColor;
        this.accentSides = accentSides.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(accentSides));
    }

    // ══════════════════════════════════════════════════════════════════
    // Builder
    // ══════════════════════════════════════════════════════════════════

    public static ModernButtonBuilder builder(Component label, OnPress onPress) {
        return new ModernButtonBuilder(label, onPress);
    }

    public static final class ModernButtonBuilder extends Builder {

        private final Component label;
        private final OnPress   onPress;
        private int x = 0, y = 0, w = 150, h = 20;
        private int             accent    = DEFAULT_ACCENT;
        private Set<AccentSide> sides     = DEFAULT_SIDES;
        private CreateNarration narration = ModernButton.DEFAULT_NARRATION;

        private ModernButtonBuilder(Component label, OnPress onPress) {
            super(label, onPress);
            this.label   = label;
            this.onPress = onPress;
        }

        public ModernButtonBuilder bounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            return this;
        }

        public ModernButtonBuilder pos(int x, int y) {
            this.x = x; this.y = y;
            return this;
        }

        public ModernButtonBuilder size(int w, int h) {
            this.w = w; this.h = h;
            return this;
        }

        /** 设置强调色（ARGB，Alpha 建议为 0xFF）。 */
        public ModernButtonBuilder accentColor(int argb) {
            this.accent = argb;
            return this;
        }

        /**
         * 指定显示色条的方向，可传入多个。
         * <ul>
         *   <li>不传参数 → 隐藏所有色条</li>
         *   <li>不调用此方法 → 默认四边全显示</li>
         * </ul>
         */
        public ModernButtonBuilder accentBar(AccentSide... sides) {
            if (sides == null || sides.length == 0) {
                this.sides = Collections.emptySet();
            } else {
                this.sides = EnumSet.copyOf(Arrays.asList(sides));
            }
            return this;
        }

        /** 自定义旁白（无障碍）生成器，一般不需要调用。 */
        public ModernButtonBuilder narration(CreateNarration narration) {
            this.narration = narration;
            return this;
        }

        public ModernButton build() {
            return new ModernButton(x, y, w, h, label, onPress, narration, accent, sides);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && isActive();
        boolean pressed = hovered && isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        hoverAnim = Mth.lerp(0.30f, hoverAnim, hovered ? 1f : 0f);

        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // ── 1. 外边框 ────────────────────────────────────────────────
        int borderColor = pressed
                ? 0xFF8899DD
                : blendColors(0xFF2A3060, 0xFF6688EE, hoverAnim);
        g.fill(x, y, x + w, y + h, borderColor);

        // ── 2. 渐变背景 ──────────────────────────────────────────────
        int bgL, bgR;
        if (!isActive()) {
            bgL = bgR = 0xFF0D1020;
        } else if (pressed) {
            bgL = blendColors(0xFF141828, 0xFF223380, hoverAnim);
            bgR = blendColors(0xFF0E1020, 0xFF162060, hoverAnim);
        } else {
            bgL = blendColors(0xFF141828, 0xFF1E2E68, hoverAnim);
            bgR = blendColors(0xFF0E1020, 0xFF162050, hoverAnim);
        }
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);

        // ── 3. 顶部高光条 ─────────────────────────────────────────────
        int topAlpha = pressed ? 0x44 : (int) (0x10 + (0x25 - 0x10) * hoverAnim);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, (topAlpha << 24) | 0xFFFFFF);

        // ── 4. 色条（叠加在背景上，按各自方向绘制）──────────────────
        if (isActive() && !accentSides.isEmpty()) {
            for (AccentSide side : accentSides) {
                renderAccentBar(g, x, y, w, h, side);
            }
        }

        // ── 5. 文字（居中于扣除色条后的剩余内容区）──────────────────
        int textColor;
        if (!isActive()) {
            textColor = 0xFF555566;
        } else if (pressed) {
            textColor = accentColor | 0xFF000000;
        } else {
            textColor = blendColors(0xFFCCCCDD, 0xFFEEEEFF, hoverAnim);
        }
        int[] tc = getTextCenter(x, y, w, h);
        g.drawCenteredString(Minecraft.getInstance().font,
                getMessage(), tc[0], tc[1], textColor);

        // ── 6. 焦点时右侧指示竖条 ────────────────────────────────────
        if (isFocused() && isActive()) {
            int indColor = blendColors(accentColor, 0xFFFFFFFF, 0.7f);
            g.fill(x + w - 4, y + 3, x + w - 1, y + h - 3, indColor);
        }
    }

    /**
     * 计算文字位置，使其居中于扣除所有激活色条后的剩余内容区域。
     *
     * <p>内容区 = 按钮背景内侧（各边 -1px 边框）再减去各激活色条的 BAR_INSET。
     * 对称方向（如同时 LEFT+RIGHT）会互相抵消，文字回到水平中心。
     *
     * @return int[]{centerX, topY}，对应 drawCenteredString 的参数。
     */
    private int[] getTextCenter(int x, int y, int w, int h) {
        boolean hasL = isActive() && accentSides.contains(AccentSide.LEFT);
        boolean hasR = isActive() && accentSides.contains(AccentSide.RIGHT);
        boolean hasT = isActive() && accentSides.contains(AccentSide.TOP);
        boolean hasB = isActive() && accentSides.contains(AccentSide.BOTTOM);

        // 内容区左右边界（背景内侧 +1，再按色条内缩）
        int contentX1 = x + 1 + (hasL ? BAR_INSET : 0);
        int contentX2 = x + w - 1 - (hasR ? BAR_INSET : 0);

        // 内容区上下边界
        int contentY1 = y + 1 + (hasT ? BAR_INSET : 0);
        int contentY2 = y + h - 1 - (hasB ? BAR_INSET : 0);

        int centerX = (contentX1 + contentX2) / 2;
        // drawCenteredString 第四参数为文字顶边 Y，9 为默认字体高度
        int topY    = contentY1 + (contentY2 - contentY1 - 9) / 2;

        return new int[]{ centerX, topY };
    }

    /** 绘制单条色条，覆盖在背景内侧，向按钮中心方向渐隐。 */
    private void renderAccentBar(GuiGraphics g, int x, int y, int w, int h, AccentSide side) {
        final int x1 = x + 1, y1 = y + 1, x2 = x + w - 1, y2 = y + h - 1;
        final int solid = accentColor | 0xFF000000;
        final int raw   = accentColor & 0x00FFFFFF;

        switch (side) {
            case LEFT -> {
                g.fill(x1, y1, x1 + BAR_THICKNESS, y2, solid);
                g.fillGradient(x1 + BAR_THICKNESS, y1,
                        x1 + BAR_THICKNESS + BAR_GLOW, y2,
                        raw | 0x40000000, 0x00000000);
            }
            case RIGHT -> {
                g.fill(x2 - BAR_THICKNESS, y1, x2, y2, solid);
                g.fillGradient(x2 - BAR_THICKNESS - BAR_GLOW, y1,
                        x2 - BAR_THICKNESS, y2,
                        0x00000000, raw | 0x40000000);
            }
            case TOP -> {
                g.fill(x1, y1, x2, y1 + BAR_THICKNESS, solid);
                g.fillGradient(x1, y1 + BAR_THICKNESS,
                        x2, y1 + BAR_THICKNESS + BAR_GLOW,
                        raw | 0x40000000, 0x00000000);
            }
            case BOTTOM -> {
                g.fill(x1, y2 - BAR_THICKNESS, x2, y2, solid);
                g.fillGradient(x1, y2 - BAR_THICKNESS - BAR_GLOW,
                        x2, y2 - BAR_THICKNESS,
                        0x00000000, raw | 0x40000000);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════

    private static boolean isMouseButtonDown(int btn) {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(handle, btn) == GLFW.GLFW_PRESS;
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >>  8) & 0xFF) + (((c2 >>  8) & 0xFF) - ((c1 >>  8) & 0xFF)) * t);
        int b = (int) (( c1        & 0xFF) + (( c2        & 0xFF) - ( c1        & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}