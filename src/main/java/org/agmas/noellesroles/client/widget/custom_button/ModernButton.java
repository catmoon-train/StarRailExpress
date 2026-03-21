package org.agmas.noellesroles.client.widget.custom_button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 仿 RoleIntroduceScreen 风格的现代按钮。
 *
 * <p>用法示例：
 * <pre>{@code
 * ModernButtonWidget btn = ModernButtonWidget.builder(
 *         Component.translatable("gui.confirm"), b -> doSomething())
 *     .bounds(x, y, 120, 20)
 *     .accentColor(0xFF5577CC)   // 可选，默认蓝紫
 *     .build();
 * addRenderableWidget(btn);
 * }</pre>
 */
public class ModernButton extends net.minecraft.client.gui.components.Button {

    private static final int DEFAULT_ACCENT = 0xFF5577CC;

    private final int accentColor;
    private float hoverAnim = 0f;

    // ── 构造（私有，通过 ModernBuilder 创建）──────────────────────────

    private ModernButton(int x, int y, int w, int h,
            Component label,
            OnPress onPress,
            CreateNarration narration,
            int accentColor) {
        super(x, y, w, h, label, onPress, narration);
        this.accentColor = accentColor;
    }
    public static ModernButtonBuilder builder(Component label, OnPress onPress) {
        return new ModernButtonBuilder(label, onPress);
    }

    public static final class ModernButtonBuilder extends Builder {

        private final Component label;
        private final OnPress onPress;
        private int x = 0, y = 0, w = 150, h = 20;
        private int accent = DEFAULT_ACCENT;
        private CreateNarration narration = ModernButton.DEFAULT_NARRATION;

        private ModernButtonBuilder(Component label, OnPress onPress) {
            super(label, onPress);
            this.label = label;
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

        /** 自定义旁白（无障碍）生成器，一般不需要调用。 */
        public ModernButtonBuilder narration(CreateNarration narration) {
            this.narration = narration;
            return this;
        }

        public ModernButton build() {
            return new ModernButton(x, y, w, h, label, onPress, narration, accent);
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
        final int raw = accentColor & 0x00FFFFFF;

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

        // ── 4. 左侧颜色强调条 + 渐隐光晕 ────────────────────────────
        if (isActive()) {
            g.fill(x + 1, y + 1, x + 4, y + h - 1, accentColor | 0xFF000000);
            g.fillGradient(x + 4, y + 1, x + 8, y + h - 1,
                    raw | 0x40000000, 0x00000000);
        }

        // ── 5. 文字 ──────────────────────────────────────────────────
        int textColor;
        if (!isActive()) {
            textColor = 0xFF555566;
        } else if (pressed) {
            textColor = accentColor | 0xFF000000;
        } else {
            textColor = blendColors(0xFFCCCCDD, 0xFFEEEEFF, hoverAnim);
        }
        int textX = x + 8 + (w - 8) / 2;
        int textY = y + (h - 9) / 2;
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), textX, textY, textColor);

        // ── 6. 焦点时右侧指示竖条 ────────────────────────────────────
        if (isFocused() && isActive()) {
            int indColor = blendColors(accentColor, 0xFFFFFFFF, 0.7f);
            g.fill(x + w - 4, y + 3, x + w - 1, y + h - 3, indColor);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════════════

    private static boolean isMouseButtonDown(int btn) {
        long handle = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(handle, btn) == GLFW.GLFW_PRESS;
    }

    /** 直接复制自 RoleIntroduceScreen，保持视觉一致。 */
    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >>  8) & 0xFF) + (((c2 >>  8) & 0xFF) - ((c1 >>  8) & 0xFF)) * t);
        int b = (int) (( c1        & 0xFF) + (( c2        & 0xFF) - ( c1        & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}