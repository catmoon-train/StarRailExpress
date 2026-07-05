package io.wifi.starrailexpress.client.render.hud.stamina;

import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.RedScreenRenderer;
import io.wifi.starrailexpress.util.ProgressProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class StaminaSplitStyleRenderer {

    private static float chargeDisplayValue = 0f; // 蓄力状态条平滑显示值（逐帧过渡用）
    public static StaminaBarRenderer view = new StaminaBarRenderer();
    public static float offsetDelta = 0f;

    private static final ResourceLocation STAMINA_ICON = Noellesroles.id("stamina/stamina_icon");
    private static final int BAR_WIDTH = 120;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 4;

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
        } else {
            if (staminaProvider != null) {
                staminaPercent = staminaProvider.getPercent();
            }
        }
        // 使用与TimeRenderer类似的颜色逻辑
        if (Math.abs(view.getTarget() - staminaPercent) > 0.1f) {
            offsetDelta = staminaPercent > view.getTarget() ? .6f : -.6f;
        }
        offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);

        view.setTarget(staminaPercent);

        // 体力条颜色 - 黄色，低于1/5时变红
        int colour = new java.awt.Color(53, 188, 122).getRGB();// 绿
        // 体力条颜色 - 黄色，低于1/5时变红
        if (staminaPercent < 0.2f) {
            colour = Mth.color(1f, 0.2f, 0.2f) | 0xFF000000; // 红
        } else if (staminaPercent < 0.6f) {
            colour = Mth.color(1f, 0.85f, 0.1f) | 0xFF000000;// 黄
        }

        if (staminaPercent >= 0) {
            // 渲染体力条 - 移动到物品栏上方
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f, context.guiHeight() - 35, 0); // 在物品栏上方显示
            {
                // 体力条
                view.renderStamina(context, colour, delta);
            }

            context.pose().popPose();
        }

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
        // 绘制体力图标
        if (staminaPercent >= 0) {
            int barCenterY = context.guiHeight() - 35;
            int iconX = context.guiWidth() / 2 - BAR_WIDTH / 2 - ICON_SIZE - ICON_GAP;
            int iconY = barCenterY - ICON_SIZE / 2;
            context.blitSprite(STAMINA_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE);
        }
    }

    public static void tick() {
        view.update();
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

        public void renderStamina(@NotNull GuiGraphics context, int colour, float delta) {
            float value = Mth.lerp(delta, this.lastValue, this.currentValue);

            // 体力条参数 - 更现代、更扁平的设计
            int barWidth = 120; // 总宽度增加
            int barHeight = 2; // 高度减小变得更扁平
            int halfWidth = barWidth / 2;

            // 绘制背景（更现代化的半透明黑色）
            int backgroundColor = 0x66000000; // 更透明的背景
            context.fill(-halfWidth, -barHeight / 2, halfWidth, barHeight / 2, backgroundColor);

            // 计算当前体力条宽度 - 从左锚定，向右延伸
            int currentWidth = Math.round(barWidth * value);

            if (currentWidth > 0) {
                // 绘制体力条（左侧固定，右侧随体力伸缩）
                context.fill(-halfWidth, -barHeight / 2, -halfWidth + currentWidth, barHeight / 2, colour);
            }
        }

        public void renderItemCharge(@NotNull GuiGraphics context, int colour, float value) {
            // 体力条参数 - 更现代、更扁平的设计
            int barWidth = 40; // 总宽度增加
            int barHeight = 6; // 高度减小变得更扁平
            int barBorder = 2; // 高度减小变得更扁平
            int halfWidth = barWidth / 2;
            colour = colour & 0x88FFFFFF;
            // 绘制背景（更现代化的半透明黑色）
            int backgroundColor = 0x55000000; // 更透明的背景
            if (value <= 0) {
                context.renderOutline(-halfWidth - barBorder, -barHeight / 2 - barBorder, halfWidth + barBorder,
                        barHeight / 2 + barBorder, backgroundColor);
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
        }

        public float getTarget() {
            return this.target;
        }
    }
}
