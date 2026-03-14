package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wifi.starrailexpress.cca.SREPlayerAFKComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AFKRenderer {
    private static final ResourceLocation AFK_TEXTURE = SRE.id("textures/gui/afk_effects.png");
    private static final Minecraft mc = Minecraft.getInstance();

    // 眨眼动画状态
    private static class BlinkAnimation {
        float progress = 0.0f;
        boolean isBlinking = false;
        boolean isClosing = true;
        long startTime = 0;
        long holdTime = 0;
        float intensity = 1.0f;
        float lastEyeClosedAmount = 0.0f;
    }

    private static final BlinkAnimation blinkAnim = new BlinkAnimation();

    // 屏幕变暗效果
    private static float darkeningAlpha = 0.0f;
    private static float darkeningTarget = 0.0f;

    // 性能优化：缓存计算结果
    private static int lastScreenWidth = 0;
    private static int lastScreenHeight = 0;
    private static float[] eyelidCurveCache;
    private static long lastCacheUpdate = 0;
    private static final int EYELID_SEGMENTS = 50; // 减少分段数以提高性能

    // 粒子效果优化：使用更少的粒子
    private static final List<SleepParticle> sleepParticles = new ArrayList<>();
    private static final Random random = new Random();
    private static long lastParticleSpawn = 0;
    private static final int MAX_PARTICLES = 20; // 限制粒子数量

    // 简化的粒子类
    private static class SleepParticle {
        float x, y;
        float velocityY;
        float size;
        float alpha;
        long spawnTime;
        int color;

        SleepParticle(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.velocityY = -0.8f - random.nextFloat() * 0.7f;
            this.size = random.nextFloat() * 2.0f + 1.0f;
            this.alpha = random.nextFloat() * 0.3f + 0.2f;
            this.spawnTime = System.currentTimeMillis();
            this.color = color;
        }

        boolean update() {
            long currentTime = System.currentTimeMillis();
            float delta = (currentTime - spawnTime) / 1000.0f;

            // 简化更新：只更新Y位置
            y += velocityY;
            velocityY += 0.02f; // 简化重力

            // 淡出
            if (delta > 1.5f) {
                alpha = 1.0f - (delta - 1.5f) / 0.5f;
            }

            return delta > 2.0f || alpha <= 0.0f || y < -20;
        }
    }

    public static void renderAFKEffects(GuiGraphics guiGraphics, float tickDelta) {
        if (mc.player == null || mc.level == null)
            return;
        if (!SREClient.isPlayerAliveAndInSurvival())
            return;
        if (!SREClient.isTrainMoving())
            return;

        LocalPlayer player = mc.player;
        SREPlayerAFKComponent afkComponent = SREPlayerAFKComponent.KEY.maybeGet(player).orElse(null);

        if (afkComponent == null)
            return;

        float afkProgress = afkComponent.getAFKProgress();
        // 只有当挂机进度超过一定阈值时才显示效果
        if (afkProgress > 0.3f) {
            // 性能优化：减少粒子更新频率
            if (afkProgress > 0.5f) {
                updateSleepParticles(afkProgress);
                renderSleepParticles(guiGraphics, tickDelta);
            }

            // 渲染眨眼效果
            if (afkProgress > 0.6f) {
                renderBlinkEffect(guiGraphics, afkProgress, tickDelta);
            }

            // 渲染屏幕变暗效果（优化版）
            if (afkProgress > 0.7f) {
                renderDarkeningEffect(guiGraphics, afkProgress, tickDelta);
            }

            // 显示挂机警告文本
            if (afkProgress > 0.8f) {
                renderAFKWarning(guiGraphics, afkProgress);
            }
        } else {
            // 重置效果
            blinkAnim.isBlinking = false;
            blinkAnim.progress = 0.0f;
            darkeningTarget = 0.0f;
        }
    }

    private static void updateSleepParticles(float afkProgress) {
        long currentTime = System.currentTimeMillis();

        // 性能优化：减少粒子生成频率（每300ms检查一次）
        if (currentTime - lastParticleSpawn > 300 && sleepParticles.size() < MAX_PARTICLES) {
            float spawnChance = (afkProgress - 0.5f) * 0.05f; // 降低生成概率
            if (random.nextFloat() < spawnChance) {
                int screenWidth = mc.getWindow().getGuiScaledWidth();

                float x = random.nextFloat() * screenWidth;
                float y = mc.getWindow().getGuiScaledHeight() + 10;

                // 简化颜色选择
                int color;
                if (afkProgress > 0.9f) {
                    color = 0xFFFF5555; // 浅红色
                } else if (afkProgress > 0.8f) {
                    color = 0xFFFFAA55; // 橙色
                } else if (afkProgress > 0.7f) {
                    color = 0xFFFFFF55; // 黄色
                } else {
                    color = 0xFF55AAFF; // 浅蓝色
                }

                sleepParticles.add(new SleepParticle(x, y, color));
                lastParticleSpawn = currentTime;
            }
        }

        // 更新粒子
        for (int i = sleepParticles.size() - 1; i >= 0; i--) {
            if (sleepParticles.get(i).update()) {
                sleepParticles.remove(i);
            }
        }
    }

    private static void renderSleepParticles(GuiGraphics guiGraphics, float tickDelta) {
        if (sleepParticles.isEmpty())
            return;

        // 简化粒子渲染：直接绘制矩形，避免矩阵变换
        for (SleepParticle particle : sleepParticles) {
            int alpha = (int) (particle.alpha * 255);
            int color = (alpha << 24) | (particle.color & 0xFFFFFF);

            // 绘制简单的矩形粒子
            int size = (int) particle.size;
            guiGraphics.fill(
                    (int) particle.x - size, (int) particle.y - size,
                    (int) particle.x + size, (int) particle.y + size,
                    color);
        }
    }

    private static void renderBlinkEffect(GuiGraphics guiGraphics, float afkProgress, float tickDelta) {
        long currentTime = System.currentTimeMillis();

        // 控制眨眼频率和强度
        float blinkIntensity = Mth.clamp((afkProgress - 0.6f) / 0.4f, 0.0f, 1.0f);
        blinkAnim.intensity = blinkIntensity;

        // 计算眨眼间隔：AFK越久，眨眼越频繁
        float blinkInterval = 3000.0f - 2000.0f * blinkIntensity; // 3秒到1秒

        // 触发新的眨眼
        if (!blinkAnim.isBlinking && currentTime - blinkAnim.startTime > blinkInterval) {
            blinkAnim.isBlinking = true;
            blinkAnim.isClosing = true;
            blinkAnim.progress = 0.0f;
            blinkAnim.startTime = currentTime;
            blinkAnim.holdTime = (long) (100 + blinkIntensity * 50); // 保持闭合时间
        }

        if (blinkAnim.isBlinking) {
            // 更新眨眼进度
            long elapsed = currentTime - blinkAnim.startTime;

            if (blinkAnim.isClosing) {
                // 闭合阶段：80ms
                blinkAnim.progress = Math.min(elapsed / 80.0f, 1.0f);

                if (blinkAnim.progress >= 1.0f && elapsed > 80 + blinkAnim.holdTime) {
                    blinkAnim.isClosing = false;
                    blinkAnim.startTime = currentTime;
                }
            } else {
                // 睁开阶段：120ms
                blinkAnim.progress = 1.0f - Math.min(elapsed / 120.0f, 1.0f);

                if (blinkAnim.progress <= 0.0f) {
                    blinkAnim.isBlinking = false;
                    blinkAnim.startTime = currentTime;
                }
            }

            // 计算闭合程度（使用平方函数使其更平滑）
            float eyeClosedAmount;
            if (blinkAnim.isClosing) {
                eyeClosedAmount = blinkAnim.progress * blinkAnim.progress * blinkAnim.intensity;
            } else {
                eyeClosedAmount = (1.0f - blinkAnim.progress) * (1.0f - blinkAnim.progress) * blinkAnim.intensity;
            }

            // 性能优化：只在闭合程度变化较大时重新渲染
            if (Math.abs(eyeClosedAmount - blinkAnim.lastEyeClosedAmount) > 0.01f) {
                renderEyelids(guiGraphics, eyeClosedAmount, blinkAnim.intensity);
                blinkAnim.lastEyeClosedAmount = eyeClosedAmount;
            }
        }
    }

    private static void updateEyelidCurveCache(int screenWidth) {
        // 缓存眼皮曲线计算，避免每帧重复计算
        if (eyelidCurveCache == null || eyelidCurveCache.length != EYELID_SEGMENTS) {
            eyelidCurveCache = new float[EYELID_SEGMENTS];
        }

        // 预计算曲线值
        for (int i = 0; i < EYELID_SEGMENTS; i++) {
            float normalizedX = (float) i / (EYELID_SEGMENTS - 1) * 2.0f - 1.0f;
            eyelidCurveCache[i] = (float) Math.cos(normalizedX * Math.PI * 0.5f);
        }

        lastCacheUpdate = System.currentTimeMillis();
    }

    private static void renderEyelids(GuiGraphics guiGraphics, float eyeClosedAmount, float intensity) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 检查是否需要更新缓存
        if (screenWidth != lastScreenWidth || eyelidCurveCache == null) {
            updateEyelidCurveCache(screenWidth);
            lastScreenWidth = screenWidth;
            lastScreenHeight = screenHeight;
        }

        // 渲染上眼皮（优化版）
        renderUpperEyelidOptimized(guiGraphics, screenWidth, screenHeight, eyeClosedAmount, intensity);

        // 渲染下眼皮（优化版）
        renderLowerEyelidOptimized(guiGraphics, screenWidth, screenHeight, eyeClosedAmount, intensity);
    }

    private static void renderUpperEyelidOptimized(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
            float closedAmount, float intensity) {
        // 性能优化：减少分段数，使用整数运算
        int segmentWidth = screenWidth / EYELID_SEGMENTS;
        float maxHeight = screenHeight * 0.25f * intensity;
        int currentHeight = (int) (maxHeight * closedAmount);

        // 使用缓存计算高度
        for (int i = 0; i < EYELID_SEGMENTS; i++) {
            int x = i * segmentWidth;
            int nextX = (i == EYELID_SEGMENTS - 1) ? screenWidth : (i + 1) * segmentWidth;

            float curve = eyelidCurveCache[i];
            int pointHeight = (int) (currentHeight * (0.7f + 0.3f * curve));

            // 简化渲染：绘制矩形区域而不是逐像素
            if (pointHeight > 0) {
                int alpha = 255 - (int) (100 * (1.0f - closedAmount));

                // 顶部渐变（简化版：只绘制一个矩形）
                guiGraphics.fill(x, 0, nextX, pointHeight, (alpha << 24) | 0x000000);

                // 边缘渐变（简化版：只绘制一条线）
                int edgeAlpha = (int) (alpha * 0.7f);
                guiGraphics.fill(x, pointHeight, nextX, pointHeight + 1, (edgeAlpha << 24) | 0x000000);
            }
        }
    }

    private static void renderLowerEyelidOptimized(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
            float closedAmount, float intensity) {
        int segmentWidth = screenWidth / EYELID_SEGMENTS;
        float maxHeight = screenHeight * 0.25f * intensity;
        int currentHeight = (int) (maxHeight * closedAmount);

        for (int i = 0; i < EYELID_SEGMENTS; i++) {
            int x = i * segmentWidth;
            int nextX = (i == EYELID_SEGMENTS - 1) ? screenWidth : (i + 1) * segmentWidth;

            float curve = eyelidCurveCache[i];
            int pointHeight = (int) (currentHeight * (0.7f + 0.3f * curve));

            if (pointHeight > 0) {
                int alpha = 255 - (int) (100 * (1.0f - closedAmount));
                int bottomY = screenHeight - pointHeight;

                // 底部渐变（简化版）
                guiGraphics.fill(x, bottomY, nextX, screenHeight, (alpha << 24) | 0x000000);

                // 边缘渐变
                int edgeAlpha = (int) (alpha * 0.7f);
                guiGraphics.fill(x, bottomY - 1, nextX, bottomY, (edgeAlpha << 24) | 0x000000);
            }
        }
    }

    private static void renderDarkeningEffect(GuiGraphics guiGraphics, float afkProgress, float tickDelta) {
        // 根据挂机进度计算目标变暗程度
        float targetDarkening = Mth.clamp((afkProgress - 0.7f) / 0.3f, 0.0f, 1.0f) * 0.6f;
        darkeningTarget = targetDarkening;

        // 平滑过渡到目标值
        float lerpSpeed = 0.1f;
        darkeningAlpha = Mth.lerp(lerpSpeed, darkeningAlpha, darkeningTarget);

        if (darkeningAlpha > 0.01f) {
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            // 性能优化：使用简单的全屏半透明矩形代替复杂的径向渐变
            int alpha = (int) (darkeningAlpha * 200); // 降低最大透明度
            guiGraphics.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0x000000);

            // 添加简单的边缘变暗效果（优化版）
            if (darkeningAlpha > 0.3f) {
                renderSimpleEdgeDarkening(guiGraphics, screenWidth, screenHeight, darkeningAlpha);
            }
        }
    }

    private static void renderSimpleEdgeDarkening(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
            float alpha) {
        // 性能优化：简化的边缘变暗效果
        int edgeSize = (int) (screenHeight * 0.1f);
        int edgeAlpha = (int) (alpha * 150);

        // 顶部边缘
        guiGraphics.fill(0, 0, screenWidth, edgeSize, (edgeAlpha << 24) | 0x000000);

        // 底部边缘
        guiGraphics.fill(0, screenHeight - edgeSize, screenWidth, screenHeight, (edgeAlpha << 24) | 0x000000);

        // 侧边边缘（较窄）
        int sideEdgeSize = (int) (screenWidth * 0.05f);
        int sideAlpha = (int) (alpha * 100);
        guiGraphics.fill(0, 0, sideEdgeSize, screenHeight, (sideAlpha << 24) | 0x000000);
        guiGraphics.fill(screenWidth - sideEdgeSize, 0, screenWidth, screenHeight, (sideAlpha << 24) | 0x000000);
    }

    private static void renderAFKWarning(GuiGraphics guiGraphics, float afkProgress) {
        if (mc.player == null)
            return;

        String warningText = "";
        int textColor;
        float scale = 1.0f;
        boolean shouldPulse = false;

        if (afkProgress > 0.95f) {
            warningText = "你要睡似了!";
            textColor = 0xFF5555; // 红色
            scale = 1.3f;
            shouldPulse = true;
        } else if (afkProgress > 0.9f) {
            warningText = "你正在深度睡眠...";
            textColor = 0xAA0000; // 深红色
            scale = 1.2f;
        } else if (afkProgress > 0.85f) {
            warningText = "你已经非常困了...";
            textColor = 0xFFAA00; // 橙色
            scale = 1.1f;
        } else if (afkProgress > 0.8f) {
            warningText = "你快要睡着了...";
            textColor = 0xFFFF00; // 黄色
        } else {
            return;
        }

        if (!warningText.isEmpty()) {
            Component textComponent = Component.literal(warningText).withStyle(ChatFormatting.BOLD);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int textWidth = (int) (mc.font.width(textComponent) * scale);
            int x = screenWidth / 2 - textWidth / 2;
            int y = 30;

            // 性能优化：只在需要时计算脉动效果
            if (shouldPulse) {
                float pulse = (Mth.sin(System.currentTimeMillis() * 0.002f) + 1.0f) * 0.5f;
                scale = 1.2f + pulse * 0.2f;
                textWidth = (int) (mc.font.width(textComponent) * scale);
                x = screenWidth / 2 - textWidth / 2;
            }

            // 保存当前变换状态
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();

            // 应用缩放
            poseStack.translate(x + textWidth / 2.0f, y, 0);
            poseStack.scale(scale, scale, 1.0f);
            poseStack.translate(-(x + textWidth / 2.0f), -y, 0);

            // 绘制简单的文本阴影
            guiGraphics.drawString(mc.font, textComponent, x + 1, y + 1, 0x000000, false);

            // 主文本
            guiGraphics.drawString(mc.font, textComponent, x, y, textColor, true);

            // 恢复变换状态
            poseStack.popPose();

            // 紧急闪烁效果（优化版）
            if (shouldPulse) {
                float flashAlpha = (Mth.sin(System.currentTimeMillis() * 0.005f) + 1.0f) * 0.5f * 0.2f;
                guiGraphics.fill(x - 5, y - 3, x + textWidth + 5, y + mc.font.lineHeight + 3,
                        ((int) (flashAlpha * 255) << 24) | 0xFF0000);
            }
        }
    }

    public static void tick() {
        // 客户端每刻执行的逻辑
        if (mc.player != null) {
            SREPlayerAFKComponent afkComponent = SREPlayerAFKComponent.KEY.maybeGet(mc.player).orElse(null);
            if (afkComponent != null) {
                // 检查是否需要重置AFK计时器
                // 这里可以添加更多判断条件
            }
        }

        // 平滑更新变暗效果
        if (Math.abs(darkeningAlpha - darkeningTarget) > 0.01f) {
            darkeningAlpha = Mth.lerp(0.05f, darkeningAlpha, darkeningTarget);
        }

        // 清理过期粒子（每10刻检查一次）
        if (mc.level != null && mc.level.getGameTime() % 10 == 0) {
            for (int i = sleepParticles.size() - 1; i >= 0; i--) {
                SleepParticle particle = sleepParticles.get(i);
                if (particle.y < -20 || particle.alpha <= 0.0f) {
                    sleepParticles.remove(i);
                }
            }
        }
    }

    // 重置所有效果（当玩家活动时调用）
    public static void resetEffects() {
        blinkAnim.isBlinking = false;
        blinkAnim.progress = 0.0f;
        darkeningAlpha = 0.0f;
        darkeningTarget = 0.0f;
        sleepParticles.clear();
    }

    // 性能监控：检查是否需要降低效果质量
    // public static boolean shouldReduceQuality() {
    // // 如果FPS低于30，减少效果质量
    // if (mc.getFps() < 30) {
    // return true;
    // }
    //
    // // 如果有很多实体，减少效果质量
    // if (mc.level != null && mc.level.getEntities().count() > 50) {
    // return true;
    // }
    //
    // return false;
    // }
}