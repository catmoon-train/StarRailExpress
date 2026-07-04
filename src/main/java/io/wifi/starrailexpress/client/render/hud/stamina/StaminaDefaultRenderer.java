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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

public class StaminaDefaultRenderer {

    private static float lastCooldown = 0f;
    private static boolean playedCooldownSound = false;
    private static ItemStack lastMainHandStack = ItemStack.EMPTY; // 用于跟踪上一次的主手物品

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
            staminaPercent = itemChargeProvider.getPercent();
        } else {
            if (staminaProvider != null) {
                staminaPercent = staminaProvider.getPercent();
            }
        }
        if (staminaPercent < 0) // 无体力条时
            return;
        // 使用与TimeRenderer类似的颜色逻辑
        if (Math.abs(view.getTarget() - staminaPercent) > 0.1f) {
            offsetDelta = staminaPercent > view.getTarget() ? .6f : -.6f;
        }
        offsetDelta = Mth.lerp(delta / 16, offsetDelta, 0f);

        view.setTarget(staminaPercent);

        // 体力条颜色 - 黄色，低于1/5时变红
        int colour = staminaPercent < 0.2f
                ? Mth.color(1f, 0.2f, 0.2f) | 0xFF000000 // 红色
                : Mth.color(1f, 0.85f, 0.1f) | 0xFF000000; // 黄色

        // 渲染主手物品冷却提示
        renderMainHandCooldown(context, player, delta);

        // 渲染体力条 - 移动到物品栏上方
        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2f, context.guiHeight() - 35, 0); // 在物品栏上方显示

        // 蓄力武器：应用"前慢后快"缓动曲线，并做逐帧平滑过渡
        if (isChargingWeapon) {
            float easedPercent = easeInCharge(staminaPercent);
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
                view.renderWithoutSmoothing(context, flashColour, displayValue);
            } else {
                view.renderWithoutSmoothing(context, colour, displayValue);
            }
        } else {
            view.render(context, colour, delta);
        }

        context.pose().popPose();

        // 绘制体力图标
        if (!isChargingWeapon) {
            int barCenterY = context.guiHeight() - 35;
            int iconX = context.guiWidth() / 2 - BAR_WIDTH / 2 - ICON_SIZE - ICON_GAP;
            int iconY = barCenterY - ICON_SIZE / 2;
            context.blitSprite(STAMINA_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE);
        }
    }

    /**
     * 渲染主手物品冷却提示
     */
    public static void renderMainHandCooldown(@NotNull GuiGraphics context, @NotNull LocalPlayer player, float delta) {
        ItemStack mainHandStack = player.getMainHandItem();
        ItemCooldowns cooldowns = player.getCooldowns();
        float cooldown = cooldowns.getCooldownPercent(mainHandStack.getItem(), delta);

        // 检查是否是同一个物品且冷却刚刚结束
        if (lastCooldown > 0 && cooldown == 0 && !playedCooldownSound
                && ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
            // 播放冷却结束音效
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f));
            playedCooldownSound = true;
        } else if (cooldown > 0 || !ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
            // 如果物品已切换，则重置冷却音效标志
            if (!ItemStack.isSameItemSameComponents(lastMainHandStack, mainHandStack)) {
                // 如果切换到刀，则播放切刀音效
                // if (mainHandStack.getItem() instanceof KnifeItem
                //         && !(lastMainHandStack.getItem() instanceof KnifeItem)) {
                //     Minecraft.getInstance().getSoundManager().play(
                //             SimpleSoundInstance.forUI(SoundEvents.IRON_GOLEM_REPAIR, 0.4f, 2.1f));
                // }
                playedCooldownSound = false;
            }
            // 如果物品仍在冷却中，重置音效标志
            if (cooldown > 0) {
                playedCooldownSound = false;
            }
        }

        // 更新上一次冷却值和物品
        lastCooldown = cooldown;
        lastMainHandStack = mainHandStack.copy();

        // 如果物品在冷却中，显示冷却百分比
        if (cooldown > 0) {
            int screenWidth = context.guiWidth();
            int screenHeight = context.guiHeight();

            // 在屏幕中心稍上方显示冷却文字
            int x = screenWidth / 2;
            int y = screenHeight - 48; // 物品栏上方

            String cooldownText = String.format("%d%%", (int) (cooldown * 100));

            // 根据冷却百分比改变颜色：红色->橙色->绿色
            int textColor;
            if (cooldown > 0.7f) {
                textColor = 0xFFFF0000; // 红色
            } else if (cooldown > 0.3f) {
                textColor = 0xFFFFA500; // 橙色
            } else {
                textColor = 0xFF00FF00; // 绿色
            }

            // 绘制文字背景（半透明黑色）
            // int textWidth = Minecraft.getInstance().font.width(cooldownText);
            // int padding = 4;
            // context.fill(
            // x - textWidth / 2 - padding,
            // y - padding,
            // x + textWidth / 2 + padding,
            // y + 9 + padding,
            // 0x80000000
            // );

            // 绘制冷却文字
            context.drawCenteredString(
                    Minecraft.getInstance().font,
                    cooldownText,
                    x,
                    y,
                    textColor);

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

        public void render(@NotNull GuiGraphics context, int colour, float delta) {
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

        public void renderWithoutSmoothing(@NotNull GuiGraphics context, int colour, float value) {
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

        public float getTarget() {
            return this.target;
        }
    }
}
