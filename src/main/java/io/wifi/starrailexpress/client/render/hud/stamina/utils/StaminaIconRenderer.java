package io.wifi.starrailexpress.client.render.hud.stamina.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;

/**
 * 体力图标渲染器：10个闪电，每个闪电有7级状态（0/6 ~ 6/6），实现连续进度。
 * 位置由外部通过 {@link GuiGraphics#pose()} 平移控制。
 */
public class StaminaIconRenderer {

    // 7个等级图标 (0/6 ~ 6/6)
    private static final ResourceLocation[] ICONS = {
        Noellesroles.id("stamina/stamina_mc_empty_icon"), // 0
        Noellesroles.id("stamina/stamina_mc_1_icon"),     // 1
        Noellesroles.id("stamina/stamina_mc_2_icon"),     // 2
        Noellesroles.id("stamina/stamina_mc_3_icon"),     // 3
        Noellesroles.id("stamina/stamina_mc_4_icon"),     // 4
        Noellesroles.id("stamina/stamina_mc_5_icon"),     // 5
        Noellesroles.id("stamina/stamina_mc_icon")        // 6
    };

    private static final int ICON_SIZE = 9;        // 图标尺寸（像素）
    private static final int GAP = -2;              // 图标间隔（像素）
    private static final int TOTAL_ICONS = 10;     // 闪电总数
    private static final int STAGES_PER_ICON = 6;  // 每个闪电的等级数（6级）
    private static final int TOTAL_STAGES = TOTAL_ICONS * STAGES_PER_ICON; // 60级

    // 闪烁状态
    private static float lastValue = -1f;
    private static long lastDecreaseTime = 0L;
    private static final long BLINK_DURATION_MS = 300L;

    /**
     * 更新体力值，检测减少并触发闪烁。
     * 每帧调用，传入当前体力百分比 (0.0 ~ 1.0)。
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
     * 从 (0,0) 开始绘制一排10个闪电，每个闪电显示0/6 ~ 6/6等级。
     * 外部调用前应平移到目标左上角。
     * @param guiGraphics 绘制上下文
     * @param value 体力百分比 (0.0 ~ 1.0)
     */
    public static void render(GuiGraphics guiGraphics, float value) {
        // 将百分比映射到总等级 (0 ~ 60)
        int totalStage = Math.round(value * TOTAL_STAGES);
        totalStage = Mth.clamp(totalStage, 0, TOTAL_STAGES);

        // 闪烁处理
        boolean blinking = System.currentTimeMillis() - lastDecreaseTime < BLINK_DURATION_MS;
        if (blinking) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // 白色闪烁
        } else {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 逐个绘制闪电
        for (int i = 0; i < TOTAL_ICONS; i++) {
            int startStage = i * STAGES_PER_ICON;
            int stage = totalStage - startStage;
            stage = Mth.clamp(stage, 0, STAGES_PER_ICON);
            ResourceLocation icon = ICONS[stage];
            int x = i * (ICON_SIZE + GAP);
            int y = 0;
            guiGraphics.blitSprite(icon, x, y, ICON_SIZE, ICON_SIZE);
        }

        // 重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}