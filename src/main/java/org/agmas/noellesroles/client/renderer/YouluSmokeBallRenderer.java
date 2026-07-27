package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.YouluSmokeBallEntity;

/**
 * 幽露球烟渲染器：一团缓慢起伏的黑色半透明烟雾球（自制球体网格）。
 * 从内外均可见；球内玩家另受视野迷雾 + 黑雾（见 {@code YouluFogColorMixin}）。
 *
 * <p>消散特效（生命后 45%）：烟雾粒子逐渐变淡、加速扩散、颜色由深变浅、
 * 透明度逐渐降低至完全消失，后期加入轻微向上飘散动效，模拟自然上升消散的物理表现。</p>
 */
@Environment(EnvType.CLIENT)
public class YouluSmokeBallRenderer extends EntityRenderer<YouluSmokeBallEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/block/white_concrete.png");

    // ==================== 显现 / 消散特效参数 ====================
    /** 初始显现动画时长（tick），前 N tick 内 alpha 从 0 渐变到满值。 */
    private static final int APPEAR_TICKS = 15;
    /** 消散阶段起始阈值：生命周期进度超过此值后进入消散阶段（55%）。留有一些余量使过渡平滑。 */
    private static final float DISSIPATION_START = 0.55f;
    /** 最大半径膨胀倍数（加速扩散）。 */
    private static final float MAX_RADIUS_MULTIPLIER = 2.8f;
    /** 最大向上飘散距离（方块格数）。 */
    private static final float MAX_Y_OFFSET = 2.0f;
    /** 消散终点颜色（浅灰，模拟烟雾变淡的视觉效果）。 */
    private static final int[] FADE_TARGET_COLOR = {180, 180, 185};
    /** 向上飘散起始阈值：消散进度超过此值后才开始上升。 */
    private static final float UP_DRIFT_THRESHOLD = 0.35f;
    /** 内层小球消散速度系数（>1.0 消散更快，增加层次感）。 */
    private static final float INNER_DISSIPATE_SPEED = 1.15f;

    public YouluSmokeBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(YouluSmokeBallEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float radius = entity.getRadius();
        // 缓慢起伏（独立于消散，始终存在）
        float pulse = (float) Math.sin((entity.tickCount + partialTick) * 0.05) * 0.03f + 1.0f;

        // 初始显现动画：前 APPEAR_TICKS tick 内 alpha 从 0 平滑过渡到 1
        float appearProgress = Math.min(1.0f, (entity.tickCount + partialTick) / APPEAR_TICKS);
        float appearAlpha = smoothstep(appearProgress);

        // 消散进度计算
        float lifeProgress = entity.tickCount / entity.getMaxLifetime();
        float d = computeDissipationProgress(lifeProgress);

        // ---- 消散特效参数 ----
        // 1) 加速扩散：使用平方曲线使后期扩散越来越快
        float radiusMultiplier = 1.0f + (MAX_RADIUS_MULTIPLIER - 1.0f) * d * d;

        // 2) 颜色由深变浅（smoothstep 插值）
        float lerp = smoothstep(d);
        int outerR = lerpColor(25, FADE_TARGET_COLOR[0], lerp);
        int outerG = lerpColor(25, FADE_TARGET_COLOR[1], lerp);
        int outerB = lerpColor(30, FADE_TARGET_COLOR[2], lerp);

        // 3) 透明度逐渐降低（叠加显现 alpha）
        float outerAlpha = 170 * (1.0f - lerp) * appearAlpha;

        // 4) 向上飘散（消散后期加速上升）
        float yOffset = computeYOffset(d);

        // 应用位置偏移（先平移再渲染球体）
        poseStack.translate(0, yOffset, 0);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // ---- 外层大球 ----
        SphereRenderHelper.renderSphere(poseStack, consumer,
                radius * pulse * radiusMultiplier, 14, 28,
                outerR, outerG, outerB, clampAlpha(outerAlpha), packedLight);

        // ---- 内层小球（消散稍快于外层，增加层次感）----
        float innerLerp = smoothstep(Math.min(1.0f, d * INNER_DISSIPATE_SPEED));
        int innerR = lerpColor(15, FADE_TARGET_COLOR[0], innerLerp);
        int innerG = lerpColor(15, FADE_TARGET_COLOR[1], innerLerp);
        int innerB = lerpColor(18, FADE_TARGET_COLOR[2], innerLerp);
        float innerAlpha = 220 * (1.0f - innerLerp) * appearAlpha;

        SphereRenderHelper.renderSphere(poseStack, consumer,
                radius * 0.9f * pulse * radiusMultiplier, 12, 24,
                innerR, innerG, innerB, clampAlpha(innerAlpha), packedLight);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 计算消散进度（0 → 1）。
     * 在 {@link #DISSIPATION_START} 之前返回 0，之后线性映射到 [0, 1]。
     */
    private static float computeDissipationProgress(float lifeProgress) {
        if (lifeProgress <= DISSIPATION_START) return 0f;
        float d = (lifeProgress - DISSIPATION_START) / (1.0f - DISSIPATION_START);
        return Math.min(1.0f, d);
    }

    /**
     * Hermite 平滑插值（smoothstep），使过渡起止平滑、中间快速。
     */
    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * 颜色通道线性插值。
     */
    private static int lerpColor(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }

    /**
     * 计算向上飘散偏移（方块格数）。
     * 消散进度超过 {@link #UP_DRIFT_THRESHOLD} 后开始加速上升，使用二次曲线模拟自然浮力。
     */
    private static float computeYOffset(float d) {
        if (d <= UP_DRIFT_THRESHOLD) return 0f;
        float upProgress = (d - UP_DRIFT_THRESHOLD) / (1.0f - UP_DRIFT_THRESHOLD);
        upProgress = Math.min(1.0f, upProgress);
        // 二次曲线加速上升，模拟烟雾受热浮力逐渐增强
        return MAX_Y_OFFSET * upProgress * upProgress;
    }

    /**
     * 将 alpha 值钳制到合法范围 [0, 255]。
     */
    private static int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, (int) alpha));
    }

    @Override
    public ResourceLocation getTextureLocation(YouluSmokeBallEntity entity) {
        return TEXTURE;
    }
}
