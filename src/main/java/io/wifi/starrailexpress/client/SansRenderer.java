package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.cca.StarPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.util.MathHelper;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector2f;

import java.util.Random;
import java.util.function.Function;

public class SansRenderer {
    public static SansRenderer instance = new SansRenderer();
    private static final float PASSIVE_THRESHOLD = .0002f;
    private static final float BT_DELAY = 5f * 20;

    public static final ResourceLocation BLOOD_TENDRILS_OVERLAY = ResourceLocation.tryBuild(SRE.MOD_ID, "textures/overlay/blood_tendrils.png");

    public static final MutableComponent[] HINTS0;
    public static final MutableComponent[] HINTS1;

    private final Minecraft m_mc;

    private StarPlayerMoodComponent m_cap;
    private PostProcessor m_post;
    private final Random m_random = new Random();
    private float m_dt;

    private float m_sanityGain;
    private float m_flashTimer;
    private float m_flashSanityGain;
    private float m_arrowTimer;
    private float m_hintTimer;
    private float m_showingHintTimer;
    private float m_maxShowingHintTimer;

    private float m_btGainedAlpha;
    private float m_btDelay;
    private float m_btAlpha;
    private double m_btTimer;

    // 添加新变量用于电影效果和模糊效果
    private float m_cinematicOffsetX = 0f;
    private float m_cinematicOffsetY = 0f;
    private float m_cinematicTimer = 0f;
    private float m_blurEffectTimer = 0f;
    private float m_blurEffectIntensity = 0f;
    private boolean m_isBlurActive = false;

    // 血丝动画相关变量
    private float m_bloodTendrilsAnimationTimer = 0f;
    private float m_bloodTendrilsIntensity = 0f;
    private float m_bloodTendrilsOffsetX = 0f;
    private float m_bloodTendrilsOffsetY = 0f;
    private float m_bloodTendrilsScale = 1.0f;
    private float m_bloodTendrilsRotation = 0f;

    // 电影效果参数
    private static final float CINEMATIC_MOVEMENT_SPEED = 0.5f;
    private static final float CINEMATIC_MOVEMENT_RANGE = 10f;
    private static final float CINEMATIC_FADE_SPEED = 0.02f;

    // 模糊效果参数
    private static final float BLUR_MAX_INTENSITY = 0.8f;
    private static final float BLUR_MIN_INTENSITY = 0.2f;
    private static final float BLUR_TRIGGER_CHANCE = 0.003f; // 每帧触发模糊的概率
    private static final float BLUR_DURATION_MIN = 20f; // 模糊最短持续时间（ticks）
    private static final float BLUR_DURATION_MAX = 60f; // 模糊最长持续时间（ticks）

    // 血丝动画参数
    private static final float BLOOD_TENDRILS_APPEAR_THRESHOLD = 0.3f; // 理智低于此值时开始出现血丝
    private static final float BLOOD_TENDRILS_MAX_INTENSITY = 0.7f; // 血丝最大强度
    private static final float BLOOD_TENDRILS_PULSE_SPEED = 0.05f; // 血丝脉动速度
    private static final float BLOOD_TENDRILS_DRIFT_SPEED = 0.002f; // 血丝漂移速度
    private static final float BLOOD_TENDRILS_SCALE_SPEED = 0.001f; // 血丝缩放速度
    private static final float BLOOD_TENDRILS_ROTATION_SPEED = 0.0005f; // 血丝旋转速度

    private MutableComponent m_hint;

    private void renderHint(Gui gui, PoseStack poseStack, float partialTicks, int scw, int sch, GuiGraphics graphics) {
        if (m_mc.player == null || m_mc.player.isCreative() || m_mc.player.isSpectator() || m_hint == null || m_cap == null || m_cap.getMood() > .36f)
            return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        poseStack.pushPose();

        // 计算电影效果偏移
        float cinematicX = m_cinematicOffsetX;
        float cinematicY = m_cinematicOffsetY;

        // 应用电影效果移动
        poseStack.translate(scw / 2d + cinematicX, sch / 2d + cinematicY, 0d);

        // 添加轻微旋转效果（如果理智很低）
        if (m_cap.getMood() < 0.2f) {
            float rotation = (float) Math.sin(m_cinematicTimer * 0.1f) * 0.5f;
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
        }

        poseStack.scale(3f, 3f, 1f);

        float o = ((int) m_showingHintTimer % 10) / 10f;
        o = ((int) m_showingHintTimer / 10) % 2 == 0 ? o : 1 - o;
        int opacity = Mth.clamp((int)(Mth.lerp(o, (m_showingHintTimer >= m_maxShowingHintTimer - 9f) || m_showingHintTimer < 10f ? 0f : .5f, 1f) * 0xFF), 0x10, 0xEF) << 24;

        float pX = -gui.getFont().width(m_hint) / 2f;
        float pY = -gui.getFont().lineHeight / 2f;

        // 添加文字阴影效果（如果理智很低）
        if (m_cap.getMood() < 0.25f) {
            int shadowColor = 0xAA0000 | (opacity >> 24 << 24); // 红色阴影
            graphics.drawString(gui.getFont(), m_hint, (int)(pX + 1), (int)(pY + 1), shadowColor, true);
        }

        graphics.drawString(gui.getFont(), m_hint, (int)pX, (int)pY, 0xFFFFFF | opacity, true);
        poseStack.popPose();
        RenderSystem.disableBlend();
    }

    public SansRenderer() {
        m_mc = Minecraft.getInstance();
    }

    static {
        HINTS0 = new MutableComponent[12];
        for (int i = 0; i < HINTS0.length; i++) {
            HINTS0[i] = Component.translatable("gui." + SRE.MOD_ID + ".hint0" + i);
        }
        HINTS1 = new MutableComponent[9];
        for (int i = 0; i < HINTS1.length; i++) {
            HINTS1[i] = Component.translatable("gui." + SRE.MOD_ID + ".hint1" + i);
        }
    }

    private void initSanityPostProcess() {
        Minecraft mc = Minecraft.getInstance();
        m_post.addSinglePassEntry("insanity", pass -> {
            return processPlayer(mc.player, cap -> {
                if (cap.getMood() > .35f && StarPlayerPsychoComponent.KEY.get(mc.player).psychoTicks <= 0)
                    return false;
                
                var effect = pass.getEffect();
                if (effect == null) return false;
                
                var desaturateUniform = effect.safeGetUniform("DesaturateFactor");
                if (desaturateUniform != null) {
                    desaturateUniform.set(MathHelper.clampNorm(Mth.inverseLerp(1, .4f, .8f)) * .69f);
                }
                
                var spreadUniform = effect.safeGetUniform("SpreadFactor");
                if (spreadUniform != null) {
                    spreadUniform.set(MathHelper.clampNorm(Mth.inverseLerp(1, .4f, .8f)) * 1.43f);
                }
                
                return true;
            });
        });
//        m_post.addSinglePassEntry("crazy", pass -> {
//            return processPlayer(mc.player, cap -> {
//                PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(mc.player);
//                if (psycho.psychoTicks <= 0)
//                    return false;
//
//                // 获取着色器效果
//                var effect = pass.getEffect();
//                if (effect == null) return false;
//
//                // 设置uniform参数
//                float gameTime = m_post.getTime() / 20.0f;
//
//                // 安全设置uniform值，添加null检查
//                var timeUniform = effect.safeGetUniform("Time");
//                if (timeUniform != null) timeUniform.set(gameTime);
//
//                var gameTimeUniform = effect.safeGetUniform("GameTime");
//                if (gameTimeUniform != null) gameTimeUniform.set(gameTime);
//
////                RenderTarget mainTarget = mc.getMainRenderTarget();
////                var screenSizeUniform = effect.safeGetUniform("ScreenSize");
////                if (screenSizeUniform != null && mainTarget != null) {
////                    screenSizeUniform.set((float)mainTarget.width, (float)mainTarget.height);
////                }
//
//                // 计算强度
//                float intensity;
//                if (GameConstants.getPsychoTimer() > 0) {
//                    intensity = Mth.clamp((GameConstants.getPsychoTimer() - psycho.psychoTicks) / (float) GameConstants.getPsychoTimer(), 0.0f, 1.0f);
//                } else {
//                    intensity = 0.5f;
//                }
//
//                var intensityUniform = effect.safeGetUniform("Intensity");
//                if (intensityUniform != null) intensityUniform.set(intensity);
//
//                var distortionUniform = effect.safeGetUniform("DistortionStrength");
//                if (distortionUniform != null) distortionUniform.set(intensity * 0.1f);
//
//                var chromaticUniform = effect.safeGetUniform("ChromaticAberration");
//                if (chromaticUniform != null) chromaticUniform.set(intensity * 0.02f);
//
//                var flickerUniform = effect.safeGetUniform("FlickerSpeed");
//                if (flickerUniform != null) flickerUniform.set(5.0f + intensity * 5.0f);
//
//                var scanlineUniform = effect.safeGetUniform("ScanlineStrength");
//                if (scanlineUniform != null) scanlineUniform.set(intensity * 0.3f);
//
//                return true;
//            });
//        });
        m_post.addSinglePassEntry("chromatical", pass -> {
            return processPlayer(mc.player, cap -> {
                if (cap.getMood() > .35f)
                    return false;
                
                var effect = pass.getEffect();
                if (effect == null) return false;
                
                var factorUniform = effect.safeGetUniform("Factor");
                if (factorUniform != null) {
                    factorUniform.set(MathHelper.clampNorm(Mth.inverseLerp(1, .4f, .8f)) * .1f);
                }
                
                var timeTotalUniform = effect.safeGetUniform("TimeTotal");
                if (timeTotalUniform != null) {
                    timeTotalUniform.set(m_post.getTime() / 20.0f);
                }
                
                return true;
            });
        });

        // 添加模糊效果后处理
//        m_post.addSinglePassEntry("blur", pass -> {
//            return processPlayer(mc.player, cap -> {
//                if (m_blurEffectIntensity <= 0.01f)
//                    return false;
//
//                // 计算模糊强度
//                float blurStrength = m_blurEffectIntensity;
//
//                // 应用模糊效果
//                pass.getEffect().safeGetUniform("BlurStrength").set(blurStrength);
//                pass.getEffect().safeGetUniform("Time").set(m_post.getTime() / 20.0f);
//
//                // 如果理智很低，增加模糊强度
//                if (cap.getMood() < 0.3f) {
//                    pass.getEffect().safeGetUniform("BlurStrength").set(blurStrength * 1.5f);
//                }
//
//                return true;
//            });
//        });
    }

    private boolean processPlayer(LocalPlayer player, Function<StarPlayerMoodComponent, Boolean> action) {
        StarPlayerMoodComponent cap = StarPlayerMoodComponent.KEY.get(player);
        return player != null &&
                (!player.isCreative() && !player.isSpectator()) &&
                cap.getMood() >= 0 &&
                action.apply(cap);
    }

    private void renderBloodTendrilsOverlay(Gui gui, PoseStack poseStack, float partialTicks, int scw, int sch) {
        if (m_mc.player == null || m_mc.player.isCreative() || m_mc.player.isSpectator())
            return;


            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // 应用血丝动画效果
            poseStack.pushPose();
//
//            // 移动到屏幕中心
//            poseStack.translate(scw / 2f, sch / 2f, 0f);

            // 应用缩放
//            float scale = m_bloodTendrilsScale;
//            poseStack.scale(scale, scale, 1f);

            // 应用旋转
            //poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(m_bloodTendrilsRotation));

            // 应用偏移
            //poseStack.translate(m_bloodTendrilsOffsetX, m_bloodTendrilsOffsetY, 0f);

            // 移回原位置
            //poseStack.translate(-scw / 2f, -sch / 2f, 0f);

            // 设置纹理
            RenderSystem.setShaderTexture(0, BLOOD_TENDRILS_OVERLAY);

            // 根据血丝强度调整透明度
            float finalAlpha = m_btAlpha * m_bloodTendrilsIntensity;

            // 渲染血丝
            renderFullscreen(poseStack, scw, sch, 100, 58, 0, 0, 100, 58, finalAlpha);

            // 如果理智非常低，渲染第二层血丝增强效果
            /*if (m_cap != null && m_cap.getMood() < 0.15f) {
                float secondaryAlpha = finalAlpha * 0.5f;
                float secondaryScale = 1.0f + (float)Math.sin(m_bloodTendrilsAnimationTimer * 0.03f) * 0.1f;

                poseStack.pushPose();
                poseStack.translate(scw / 2f, sch / 2f, 0f);
                poseStack.scale(secondaryScale, secondaryScale, 1f);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-m_bloodTendrilsRotation * 0.7f));
                poseStack.translate(-m_bloodTendrilsOffsetX * 1.3f, -m_bloodTendrilsOffsetY * 1.3f, 0f);
                poseStack.translate(-scw / 2f, -sch / 2f, 0f);

                renderFullscreen(poseStack, scw, sch, 100, 58, 0, 0, 100, 58, secondaryAlpha);
                poseStack.popPose();
            }*/

            poseStack.popPose();
            RenderSystem.disableBlend();

    }

    public void tick(@NotNull LocalPlayer player, @NotNull GuiGraphics context, float dt) {
        if (m_mc.player == null || m_mc.isPaused() || m_mc.player.isCreative() || m_mc.player.isSpectator() || !StarGameWorldComponent.KEY.get(player.level()).isRunning())
            return;

        m_cap = StarPlayerMoodComponent.KEY.get(m_mc.player);
        if (m_cap == null)
            return;

        m_dt = dt;

        if (m_flashTimer > 0)
            m_flashTimer -= dt;

        m_sanityGain = m_cap.getMood();
        if (Math.abs(m_sanityGain) >= 0.01f)
            m_flashTimer = 20;
        m_flashSanityGain = m_flashTimer <= 0 ? 0 : m_flashSanityGain + m_sanityGain;

        if (m_arrowTimer <= 0)
            m_arrowTimer = 23.99f;

        tickHint(dt);
        tickBt(dt);

        // 更新电影效果
        updateCinematicEffect(dt);

        // 更新模糊效果
        updateBlurEffect(dt);

        // 更新血丝动画
        updateBloodTendrilsAnimation(dt);

        // 修复renderHint调用，传入正确的参数
        if (m_mc.player != null && m_hint != null && m_cap != null) {
            renderHint(new Gui(m_mc), context.pose(), dt, m_mc.getWindow().getGuiScaledWidth(), m_mc.getWindow().getGuiScaledHeight(), context);
        }
        if (m_cap != null && m_cap.getMood() <= .36f) {
            renderBloodTendrilsOverlay(new Gui(m_mc), context.pose(), dt, m_mc.getWindow().getGuiScaledWidth(), m_mc.getWindow().getGuiScaledHeight());
        }
    }

    /**
     * 更新电影效果（文字缓慢移动）
     */
    private void updateCinematicEffect(float dt) {
        m_cinematicTimer += dt * CINEMATIC_MOVEMENT_SPEED;

        // 使用正弦和余弦函数创建平滑的圆周运动
        m_cinematicOffsetX = (float) (Math.sin(m_cinematicTimer) * CINEMATIC_MOVEMENT_RANGE);
        m_cinematicOffsetY = (float) (Math.cos(m_cinematicTimer * 0.7f) * CINEMATIC_MOVEMENT_RANGE * 0.7f);

        // 如果理智很低，增加运动幅度
        if (m_cap != null && m_cap.getMood() < 0.3f) {
            m_cinematicOffsetX *= 1.5f;
            m_cinematicOffsetY *= 1.5f;
        }
    }

    /**
     * 更新模糊效果
     */
    private void updateBlurEffect(float dt) {
        // 如果理智低于阈值，有概率触发模糊效果
        if (m_cap != null && m_cap.getMood() < 0.5f) {
            // 根据理智值调整触发概率
            float triggerChance = BLUR_TRIGGER_CHANCE * (1.0f - m_cap.getMood() * 1.5f);

            // 随机触发模糊效果
            if (m_random.nextFloat() < triggerChance && !m_isBlurActive) {
                m_isBlurActive = true;
                m_blurEffectTimer = BLUR_DURATION_MIN + m_random.nextFloat() * (BLUR_DURATION_MAX - BLUR_DURATION_MIN);
                m_blurEffectIntensity = BLUR_MIN_INTENSITY + m_random.nextFloat() * (BLUR_MAX_INTENSITY - BLUR_MIN_INTENSITY);
            }
        }

        // 更新模糊效果计时器
        if (m_isBlurActive) {
            m_blurEffectTimer -= dt;

            // 模糊效果淡入淡出
            if (m_blurEffectTimer > m_blurEffectIntensity * 10f) {
                // 淡入
                m_blurEffectIntensity = Math.min(m_blurEffectIntensity + dt * 0.05f, BLUR_MAX_INTENSITY);
            } else if (m_blurEffectTimer < 20f) {
                // 淡出
                m_blurEffectIntensity = Math.max(m_blurEffectIntensity - dt * 0.05f, 0f);
            }

            // 如果计时器结束，关闭模糊效果
            if (m_blurEffectTimer <= 0f) {
                m_isBlurActive = false;
                m_blurEffectIntensity = 0f;
            }
        }
    }

    /**
     * 更新血丝动画
     */
    private void updateBloodTendrilsAnimation(float dt) {
        m_bloodTendrilsAnimationTimer += dt;

        // 根据理智值计算血丝强度
        if (m_cap != null) {
            float mood = m_cap.getMood();
            if (mood < BLOOD_TENDRILS_APPEAR_THRESHOLD) {
                // 理智越低，血丝强度越高
                float targetIntensity = Mth.clamp(1.0f - (mood / BLOOD_TENDRILS_APPEAR_THRESHOLD), 0f, 1.0f) * BLOOD_TENDRILS_MAX_INTENSITY;
                m_bloodTendrilsIntensity = Mth.lerp(0.1f, m_bloodTendrilsIntensity, targetIntensity);
            } else {
                // 理智高时淡出血丝
                m_bloodTendrilsIntensity = Mth.lerp(0.05f, m_bloodTendrilsIntensity, 0f);
            }
        }

        // 更新血丝动画参数
        if (m_bloodTendrilsIntensity > 0.01f) {
            // 脉动效果
            float pulse = (float) (Math.sin(m_bloodTendrilsAnimationTimer * BLOOD_TENDRILS_PULSE_SPEED) * 0.1f + 1.0f);
            m_bloodTendrilsScale = 1.0f + pulse * m_bloodTendrilsIntensity * 0.2f;

            // 漂移效果
            m_bloodTendrilsOffsetX = (float) Math.sin(m_bloodTendrilsAnimationTimer * BLOOD_TENDRILS_DRIFT_SPEED) * 20f * m_bloodTendrilsIntensity;
            m_bloodTendrilsOffsetY = (float) Math.cos(m_bloodTendrilsAnimationTimer * BLOOD_TENDRILS_DRIFT_SPEED * 0.8f) * 15f * m_bloodTendrilsIntensity;

            // 旋转效果（如果理智非常低）
            if (m_cap != null && m_cap.getMood() < 0.2f) {
                m_bloodTendrilsRotation = (float) (m_bloodTendrilsAnimationTimer * BLOOD_TENDRILS_ROTATION_SPEED * 360f) % 360f;
            } else {
                m_bloodTendrilsRotation = 0f;
            }
        } else {
            // 重置动画参数
            m_bloodTendrilsScale = 1.0f;
            m_bloodTendrilsOffsetX = 0f;
            m_bloodTendrilsOffsetY = 0f;
            m_bloodTendrilsRotation = 0f;
        }
    }

    private void tickHint(float dt) {
        if (m_cap.getMood() <= .4f)
            return;

        if (m_hintTimer <= 0f && m_showingHintTimer <= 0f) {
            int id;
            if (m_cap.getMood() <= .7f) {
                id = m_random.nextInt(HINTS0.length);
                m_hint = HINTS0[id];
                m_hintTimer = 2000;
            } else {
                id = m_random.nextInt(HINTS1.length);
                m_hint = HINTS1[id];
                m_hintTimer = 600;
            }

            m_showingHintTimer = (m_maxShowingHintTimer = 199f);

            // 当新提示出现时，重置电影效果计时器以获得平滑过渡
            m_cinematicTimer = 0f;
        }
        if (m_showingHintTimer > 0f)
            m_showingHintTimer -= dt;
        else
            m_hintTimer = MathHelper.clamp(m_hintTimer - dt, 0, Float.MAX_VALUE);
    }

    private void tickBt(float dt) {
        ResourceLocation dim = m_mc.player.level().dimension().location();
        boolean flash = true;
        boolean passive = true;

        if (m_sanityGain >= .002f && flash)
            m_btGainedAlpha = Mth.lerp(MathHelper.clampNorm(Mth.inverseLerp(m_sanityGain, .002f, .02f)), .4f, .75f);

        if (m_btGainedAlpha > 0f && flash) {
            if (m_btAlpha < m_btGainedAlpha)
                m_btAlpha = Mth.clamp(m_btAlpha + .5f, 0f, m_btGainedAlpha);
            else
                m_btGainedAlpha = 0f;
        } else if (m_btDelay >= BT_DELAY && passive) {
            if (m_btAlpha < .15f) {
                m_btTimer = 0;
                m_btAlpha = Mth.clamp(m_btAlpha + .1f, m_btAlpha, .15f);
            } else if (m_btAlpha > .3f) {
                m_btTimer = Mth.PI / .2f;
                m_btAlpha = Mth.clamp(m_btAlpha - .1f, .3f, m_btAlpha);
            } else {
                m_btAlpha = Mth.lerp((-Mth.cos((float)m_btTimer * .2f) + 1f) * .5f, .15f, .3f);
                m_btTimer += m_dt;
            }
        } else
            m_btAlpha = Mth.clamp(m_btAlpha - .1f, 0f, m_btAlpha);
    }

    public void initPostProcessor() {
        if (m_post != null) return;

        m_post = new PostProcessor();
        initSanityPostProcess();
    }

    public PostProcessor getPostProcessor() {
        return m_post;
    }

    public void renderPostProcess(float partialTicks) {
        if (m_post == null) return;

        m_post.render(partialTicks);
    }

    public void resize(int w, int h) {
        if (m_post == null) return;

        m_post.resize(w, h);
    }

    private static void renderFullscreen(PoseStack poseStack, int scw, int sch, int texw, int texh, int uoffset, int voffset, int spritew, int spriteh, float alpha) {
        Matrix4f mat = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        final var begin = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        begin.addVertex(mat, 0f, 0f, 0f).setColor(1f, 1f, 1f, alpha).setUv((float)uoffset / texw, (float)voffset / texh);
        begin.addVertex(mat, 0f, (float)sch, 0f).setColor(1f, 1f, 1f, alpha).setUv((float)uoffset / texw, (float)(voffset + spriteh) / texh);
        begin.addVertex(mat, (float)scw, (float)sch, 0f).setColor(1f, 1f, 1f, alpha).setUv((float)(uoffset + spritew) / texw, (float)(voffset + spriteh) / texh);
        begin.addVertex(mat, (float)scw, 0f, 0f).setColor(1f, 1f, 1f, alpha).setUv((float)(uoffset + spritew) / texw, (float)voffset / texh);
        BufferUploader.drawWithShader(begin.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * 重置所有视觉效果（例如当玩家切换维度或游戏状态改变时）
     */
    public void resetVisualEffects() {
        m_cinematicOffsetX = 0f;
        m_cinematicOffsetY = 0f;
        m_cinematicTimer = 0f;
        m_blurEffectTimer = 0f;
        m_blurEffectIntensity = 0f;
        m_isBlurActive = false;
        m_bloodTendrilsAnimationTimer = 0f;
        m_bloodTendrilsIntensity = 0f;
        m_bloodTendrilsOffsetX = 0f;
        m_bloodTendrilsOffsetY = 0f;
        m_bloodTendrilsScale = 1.0f;
        m_bloodTendrilsRotation = 0f;
    }
}