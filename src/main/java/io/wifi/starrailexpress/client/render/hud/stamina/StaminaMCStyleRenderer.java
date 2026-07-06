package io.wifi.starrailexpress.client.render.hud.stamina;

import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.RedScreenRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.StaminaIconRenderer;
import io.wifi.starrailexpress.util.ProgressProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class StaminaMCStyleRenderer {

    private static float chargeDisplayValue = 0f; // 蓄力状态条平滑显示值（逐帧过渡用）
    public static ChargeBarRenderer view = new ChargeBarRenderer();

    private static final long FLASH_DURATION_MS = 250L; // 闪光持续时间（毫秒）

    // 添加刀蓄满力的视觉效果相关变量
    private static boolean knifeFullyCharged = false;
    private static long flashStartTime = 0L; // 闪光开始时间（毫秒）

    public static void render(LocalPlayer player, ItemStack mainHandStack, GuiGraphics context, float delta,
            ProgressProvider staminaProvider,
            ProgressProvider itemChargeProvider, boolean isChargingWeapon) {
        float staminaPercent = -1;
        float itemPercent = -1;
        if (isChargingWeapon) {
            // 处理蓄力完成效果
            if (itemChargeProvider.getPercent() >= 1.0f && !knifeFullyCharged) { // 重用knifeFullyCharged变量作为通用蓄力完成标志
                knifeFullyCharged = true;
                flashStartTime = System.currentTimeMillis(); // 开始闪光效果
                RedScreenRenderer.screenRedEffectStartTime = System.currentTimeMillis(); // 触发屏幕红色效果
                // 调用蓄力完成回调
            } else if (itemChargeProvider.getPercent() < 1.0f) {
                knifeFullyCharged = false;
            }
            itemPercent = itemChargeProvider.getPercent();
        }

        if (staminaProvider != null) {
            staminaPercent = staminaProvider.getPercent();
        }
        if (staminaPercent >= 0) {
            // 渲染体力条 - 移动到物品栏上方
            // 1. 更新状态（每帧都传当前体力值）
            StaminaIconRenderer.update(staminaPercent);
            int heartX = context.guiWidth() / 2 - 91; // 第一颗心的 X 坐标
            int heartY = context.guiHeight() - 36; // 心的 Y 坐标（距底部 49 像素）
            // 2. 将坐标系平移到您想要的左上角位置（例如 x=10, y=20）
            context.pose().pushPose();
            context.pose().translate(heartX, heartY, 0);

            // 3. 在 (0,0) 处绘制图标
            StaminaIconRenderer.render(context, staminaPercent);

            context.pose().popPose();

        }

        // 渲染主手物品冷却提示

        {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2 + 10, 0); // 在物品栏上方显示
            // 条颜色 - 黄色，低于1/5时变红
            int chargeColour = Mth.color(1f, 0.2f, 0.2f) | 0xFF000000;// red

            if (itemPercent < 0.2f) {
                chargeColour = new java.awt.Color(53, 188, 122).getRGB(); // green
            } else if (itemPercent < 0.6f) {
                chargeColour = Mth.color(1f, 0.85f, 0.1f) | 0xFF000000;// 黄
            }
            // 蓄力武器：应用"前慢后快"缓动曲线，并做逐帧平滑过渡。渲染在鼠标下面，如PEAK
            if (isChargingWeapon) {
                float easedPercent = easeInCharge(itemPercent);
                float displayValue;
                if (SREClientConfig.instance().disableStaminaBarSmoothing) {
                    // 配置禁用平滑：直接显示缓动后的值
                    displayValue = easedPercent;
                    chargeDisplayValue = easedPercent;
                } else {
                    // 逐帧向缓动目标平滑靠拢，消除按tick取值带来的阶梯跳变
                    float lerpFactor = Mth.clamp(delta * 0.5f, 0f, 1f);
                    chargeDisplayValue = Mth.lerp(lerpFactor, chargeDisplayValue, easedPercent);
                    if (Math.abs(chargeDisplayValue - easedPercent) < 0.005f) {
                        chargeDisplayValue = easedPercent;
                    }
                    displayValue = chargeDisplayValue;
                }
                // 如果是刀且完全蓄力，则添加特殊效果
                if (ChargeableItemRegistry.hasSpecialVisualEffects(mainHandStack, player) && knifeFullyCharged
                        && isFlashActive()) {
                    // 创建闪烁效果
                    int flashColour = getFlashColor(); // 红白交替闪烁
                    view.renderItemCharge(context, flashColour, displayValue);
                } else {
                    view.renderItemCharge(context, chargeColour, displayValue);
                }
            }
            context.pose().popPose();
        }
    }

    /**
     * 蓄力状态条缓动曲线：前慢后快（二次缓入 ease-in）。
     * 输入为线性蓄力进度 0~1，输出为显示进度——前段增长慢、临近满蓄时加速冲满。
     */
    private static float easeInCharge(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t;
    }

    /**
     * 检查闪动效果是否仍然活跃
     */
    private static boolean isFlashActive() {
        if (flashStartTime == 0L) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return (currentTime - flashStartTime) < FLASH_DURATION_MS;
    }

    /**
     * 获取当前闪动颜色
     */
    private static int getFlashColor() {
        if (flashStartTime == 0L) {
            return 0xFFFFFFFF; // 默认白色
        }
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - flashStartTime;
        // 使用更长的周期让闪烁更明显
        long cycleDuration = 100L; // 100毫秒一个周期
        long cyclePosition = elapsed % cycleDuration;
        // 在周期的前半段显示红色，在后半段显示白色
        return (cyclePosition < cycleDuration / 2) ? 0xFFFF0000 : 0xFFFFFFFF;
    }

    public static class StaminaBarRenderer {
        // 7 个进度图标
        final int ICON_SIZE = 9;
        private static final ResourceLocation STAMINA_EMPTY = Noellesroles.id("stamina/stamina_mc_empty_icon");
        private static final ResourceLocation STAMINA_1 = Noellesroles.id("stamina/stamina_mc_1_icon");
        private static final ResourceLocation STAMINA_2 = Noellesroles.id("stamina/stamina_mc_2_icon");
        private static final ResourceLocation STAMINA_3 = Noellesroles.id("stamina/stamina_mc_3_icon");
        private static final ResourceLocation STAMINA_4 = Noellesroles.id("stamina/stamina_mc_4_icon");
        private static final ResourceLocation STAMINA_5 = Noellesroles.id("stamina/stamina_mc_5_icon");
        private static final ResourceLocation STAMINA_FULL = Noellesroles.id("stamina/stamina_mc_icon");

        private float target;
        private float currentValue;
        private float lastValue;

        public void setTarget(float target) {
            this.target = Mth.clamp(target, 0f, 1f);
        }

        public void update() {
            this.lastValue = this.currentValue;
            this.currentValue = Mth.lerp(0.15f, this.currentValue, this.target);
            if (Math.abs(this.currentValue - this.target) < 0.01f) {
                this.currentValue = this.target;
            }
        }

        public void render(@NotNull GuiGraphics context, int colour, float delta) {
            float value = Mth.lerp(delta, this.lastValue, this.currentValue);
            renderBar(context, value);
        }

        public void renderWithoutSmoothing(@NotNull GuiGraphics context, int colour, float value) {
            renderBar(context, value);
        }

        /**
         * 根据进度值选择 0/6 ~ 6/6 图标绘制
         */
        private void renderBar(@NotNull GuiGraphics context, float value) {
            // 将 0~1 映射到 0~6（四舍五入）
            int level = Math.round(value * 6);
            level = Mth.clamp(level, 0, 6);

            // 根据等级选择图标
            ResourceLocation icon;
            switch (level) {
                case 0:
                    icon = STAMINA_EMPTY;
                    break;
                case 1:
                    icon = STAMINA_1;
                    break;
                case 2:
                    icon = STAMINA_2;
                    break;
                case 3:
                    icon = STAMINA_3;
                    break;
                case 4:
                    icon = STAMINA_4;
                    break;
                case 5:
                    icon = STAMINA_5;
                    break;
                default:
                    icon = STAMINA_FULL;
                    break;
            }

            // 居中绘制图标（假设 context 已 translate 到目标位置）
            int halfSize = ICON_SIZE / 2; // ICON_SIZE = 9（在外部类定义）
            context.blitSprite(icon, -halfSize, -halfSize, ICON_SIZE, ICON_SIZE);
        }

        public float getTarget() {
            return this.target;
        }
    }

    public static class ChargeBarRenderer {

        public void renderItemCharge(@NotNull GuiGraphics context, int colour, float value) {
            RenderSystem.enableBlend();

            // 体力条参数 - 更现代、更扁平的设计
            int barWidth = 40; // 总宽度增加
            int barHeight = 6; // 高度减小变得更扁平
            int barBorder = 2; // 高度减小变得更扁平
            int halfWidth = barWidth / 2;
            colour = colour & 0x88FFFFFF;
            // 绘制背景（更现代化的半透明黑色）
            int backgroundColor = 0x55000000; // 更透明的背景
            if (value <= 0) {
                renderOutline(context, -halfWidth - barBorder, -barHeight / 2 - barBorder, halfWidth + barBorder,
                        barHeight / 2 + barBorder, 1, backgroundColor);
            } else {
                context.fill(-halfWidth - barBorder, -barHeight / 2 - barBorder, halfWidth + barBorder,
                        barHeight / 2 + barBorder, backgroundColor);
            }

            // 计算当前体力条宽度 - 从左锚定，向右延伸
            int currentWidth = Math.round(barWidth * value);

            if (currentWidth > 0) {
                // 绘制体力条（左侧固定，右侧随体力伸缩）
                context.fill(-halfWidth, -barHeight / 2, -halfWidth + currentWidth, barHeight / 2, colour);
            }
            RenderSystem.disableBlend();

        }

        private void renderOutline(GuiGraphics context, int x1, int y1, int x2, int y2, int width,
                int backgroundColor) {
            // 如果边框宽度 <= 0，则不绘制
            if (width <= 0)
                return;

            // 规范化坐标，确保 left<right, top<bottom
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            int top = Math.min(y1, y2);
            int bottom = Math.max(y1, y2);

            // 1. 上边框
            context.fill(left, top, right, top + width, backgroundColor);
            // 2. 下边框
            context.fill(left, bottom - width, right, bottom, backgroundColor);
            // 3. 左边框（避开上下边框已占用的区域，防止重叠绘制造成的视觉问题）
            context.fill(left, top + width, left + width, bottom - width, backgroundColor);
            // 4. 右边框
            context.fill(right - width, top + width, right, bottom - width, backgroundColor);
        }
    }

    public static void tick() {
        // 如果不在使用蓄力物品，重置蓄力状态
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ItemStack mainHandStack = minecraft.player.getMainHandItem();
            // 检查是否不是蓄力物品
            if (!ChargeableItemRegistry.isChargeableStack(mainHandStack)) {
                knifeFullyCharged = false;
                flashStartTime = 0L;
                RedScreenRenderer.screenRedEffectStartTime = 0L;
                chargeDisplayValue = 0f; // 重置蓄力状态条平滑值，下次蓄力从 0 开始
            }
        }
    }
}
