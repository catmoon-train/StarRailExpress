package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;

/**
 * 纯体力图标渲染器 - 绘制一排10个闪电图标，位置由外部控制。
 * <p>外部需在调用前通过 {@link GuiGraphics#pose()} 平移到目标左上角。</p>
 */
public class StaminaIconRenderer {

    // 闪电图标（空/满）
    private static final ResourceLocation LIGHTNING_EMPTY = Noellesroles.id("stamina/lightning_empty");
    private static final ResourceLocation LIGHTNING_FULL  = Noellesroles.id("stamina/lightning_full");

    private static final int ICON_SIZE = 9;      // 图标大小（像素），与原版心一致
    private static final int GAP = 2;            // 图标间隔（像素）
    private static final int TOTAL_ICONS = 10;   // 总共10个闪电

    // 闪烁状态
    private static float lastValue = -1f;
    private static long lastDecreaseTime = 0L;
    private static final long BLINK_DURATION_MS = 300L;

    /**
     * 更新体力值，用于检测减少并触发闪烁。
     * 应由外部每帧调用，传入当前体力百分比 (0.0 ~ 1.0)。
     */
    public static void update(float currentValue) {
        if (lastValue < 0) {
            lastValue = currentValue;
        } else if (currentValue < lastValue - 0.001f) {
            lastDecreaseTime = System.currentTimeMillis();
        }
        lastValue = currentValue;
    }

    /**
     * 从 (0,0) 开始绘制一排10个闪电图标。
     * 根据 value 计算出需要点亮多少个，未点亮部分显示为空。
     * @param guiGraphics 绘制上下文
     * @param value 体力百分比 (0.0 ~ 1.0)
     */
    public static void render(GuiGraphics guiGraphics, float value) {
        // 计算应点亮的图标数量（四舍五入）
        int filled = Math.round(value * TOTAL_ICONS);
        filled = Mth.clamp(filled, 0, TOTAL_ICONS);

        // 判断是否处于闪烁状态
        boolean blinking = System.currentTimeMillis() - lastDecreaseTime < BLINK_DURATION_MS;

        // 闪烁时强制使用白色（默认纹理颜色），否则正常绘制
        if (!blinking) {
            // 非闪烁：恢复默认颜色（通常会保持纹理原色，但为了安全显式设置）
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } // 闪烁时也保持白色，但这里是默认，也可不额外设置

        // 逐个绘制
        for (int i = 0; i < TOTAL_ICONS; i++) {
            ResourceLocation icon = (i < filled) ? LIGHTNING_FULL : LIGHTNING_EMPTY;
            int x = i * (ICON_SIZE + GAP);
            int y = 0;
            guiGraphics.blitSprite(icon, x, y, ICON_SIZE, ICON_SIZE);
        }

        // 重置颜色（确保不影响后续绘制）
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}