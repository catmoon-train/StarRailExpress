package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;

/**
 * 纯体力图标渲染器 - 只负责绘制图标，位置由外部控制。
 * <p>外部需在调用前通过 {@link GuiGraphics#pose()} 平移到目标位置。</p>
 */
public class StaminaIconRenderer {

    // 7 个等级图标
    private static final ResourceLocation STAMINA_EMPTY = Noellesroles.id("stamina/stamina_mc_empty_icon");
    private static final ResourceLocation STAMINA_1   = Noellesroles.id("stamina/stamina_mc_1_icon");
    private static final ResourceLocation STAMINA_2   = Noellesroles.id("stamina/stamina_mc_2_icon");
    private static final ResourceLocation STAMINA_3   = Noellesroles.id("stamina/stamina_mc_3_icon");
    private static final ResourceLocation STAMINA_4   = Noellesroles.id("stamina/stamina_mc_4_icon");
    private static final ResourceLocation STAMINA_5   = Noellesroles.id("stamina/stamina_mc_5_icon");
    private static final ResourceLocation STAMINA_FULL= Noellesroles.id("stamina/stamina_mc_icon");

    private static final int ICON_SIZE = 9;    // 图标大小（像素），与原版心一致

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
     * 在 (0,0) 处绘制体力图标。
     * <p>外部应在调用前通过 {@link GuiGraphics#pose()} 平移到目标左上角位置。</p>
     * @param guiGraphics 绘制上下文
     * @param value 体力百分比 (0.0 ~ 1.0)
     */
    public static void render(GuiGraphics guiGraphics, float value) {
        // 根据 value 选择图标等级 (0~6)
        int level = Math.round(value * 6);
        level = Mth.clamp(level, 0, 6);
        ResourceLocation icon;
        switch (level) {
            case 0: icon = STAMINA_EMPTY; break;
            case 1: icon = STAMINA_1;    break;
            case 2: icon = STAMINA_2;    break;
            case 3: icon = STAMINA_3;    break;
            case 4: icon = STAMINA_4;    break;
            case 5: icon = STAMINA_5;    break;
            default: icon = STAMINA_FULL; break;
        }

        // 闪烁时绘制为白色（默认已白色，也可改为其他颜色）
        boolean blinking = System.currentTimeMillis() - lastDecreaseTime < BLINK_DURATION_MS;
        if (blinking) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // 纯白
        } else {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 在 (0,0) 绘制图标
        guiGraphics.blitSprite(icon, 0, 0, ICON_SIZE, ICON_SIZE);

        // 重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}