/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import org.agmas.noellesroles.content.entity.YouluSmokeWaveEntity;

/**
 * 幽露「遮天闭目」烟雾波渲染器：一个穿墙推进的小型烟雾球。
 *
 * <p>视觉上为 60% 大小的球烟，向前穿墙推进 30 格后消散。
 * 推进最后 20% 距离触发消散特效：颜色由深变浅、透明度降低、加速扩散。</p>
 */
@Environment(EnvType.CLIENT)
public class YouluSmokeWaveRenderer extends EntityRenderer<YouluSmokeWaveEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/block/white_concrete.png");

    // ==================== 显现 / 消散参数 ====================
    /** 初始显现动画时长（tick）。 */
    private static final int APPEAR_TICKS = 12;
    /** 消散起始阈值：推进进度超过此值后进入消散阶段（剩余 20% 距离时）。 */
    private static final float DISSIPATION_START = 0.80f;
    /** 消散阶段最大半径膨胀倍数。 */
    private static final float MAX_RADIUS_MULTIPLIER = 2.5f;
    /** 消散终点颜色（浅灰）。 */
    private static final int[] FADE_TARGET_COLOR = {180, 180, 185};
    /** 内层小球消散速度系数。 */
    private static final float INNER_DISSIPATE_SPEED = 1.15f;

    public YouluSmokeWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(YouluSmokeWaveEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float radius = entity.getRadius();
        // 缓慢起伏
        float pulse = (float) Math.sin((entity.tickCount + partialTick) * 0.05) * 0.03f + 1.0f;

        // 初始显现动画
        float appearProgress = Math.min(1.0f, (entity.tickCount + partialTick) / APPEAR_TICKS);
        float appearAlpha = smoothstep(appearProgress);

        // 推进进度：已走过距离 / 总距离
        float distTraveled = entity.getMaxDistance() - (float) entity.getRemainingDistance();
        float travelProgress = Math.min(1.0f, distTraveled / entity.getMaxDistance());

        // 消散进度：最后 20% 距离触发
        float d = computeDissipationProgress(travelProgress);

        // ---- 消散特效 ----
        // 1) 加速扩散
        float radiusMultiplier = 1.0f + (MAX_RADIUS_MULTIPLIER - 1.0f) * d * d;

        // 2) 颜色由深变浅
        float lerp = smoothstep(d);
        int outerR = lerpColor(25, FADE_TARGET_COLOR[0], lerp);
        int outerG = lerpColor(25, FADE_TARGET_COLOR[1], lerp);
        int outerB = lerpColor(30, FADE_TARGET_COLOR[2], lerp);

        // 3) 透明度逐渐降低（叠加显现 alpha）
        float outerAlpha = 170 * (1.0f - lerp) * appearAlpha;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // ---- 外层大球 ----
        SphereRenderHelper.renderSphere(poseStack, consumer,
                radius * pulse * radiusMultiplier, 14, 28,
                outerR, outerG, outerB, clampAlpha(outerAlpha), packedLight);

        // ---- 内层小球（消散稍快）----
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

    private static float computeDissipationProgress(float travelProgress) {
        if (travelProgress <= DISSIPATION_START) return 0f;
        float d = (travelProgress - DISSIPATION_START) / (1.0f - DISSIPATION_START);
        return Math.min(1.0f, d);
    }

    private static float smoothstep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static int lerpColor(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }

    private static int clampAlpha(float alpha) {
        return Math.max(0, Math.min(255, (int) alpha));
    }

    @Override
    public ResourceLocation getTextureLocation(YouluSmokeWaveEntity entity) {
        return TEXTURE;
    }
}
