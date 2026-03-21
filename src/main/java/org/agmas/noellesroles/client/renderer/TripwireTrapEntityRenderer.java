package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.entity.TripwireTrapEntity;
import org.joml.Matrix4f;

/**
 * 绊索陷阱实体渲染器
 * 
 * 对所有玩家可见，渲染为橙色绊线效果
 */
public class TripwireTrapEntityRenderer extends EntityRenderer<TripwireTrapEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/misc/enchanted_glint_entity.png");

    public TripwireTrapEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TripwireTrapEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {

        matrices.pushPose();

        // 旋转使其平躺在地面上
        matrices.mulPose(Axis.XP.rotationDegrees(90.0f));

        // 缩放 - 绊索陷阱比灾厄印记稍小
        float scale = 0.6f;
        // 添加轻微脉动效果
        float pulse = (float) Math.sin((entity.tickCount + tickDelta) * 0.15) * 0.05f + 1.0f;
        matrices.scale(scale * pulse, scale * pulse, scale);

        // 渲染绊索标记
        renderTripwireMark(matrices, vertexConsumers, light);

        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * 渲染绊索标记图形（十字线形状）
     */
    private void renderTripwireMark(PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        PoseStack.Pose entry = matrices.last();
        Matrix4f posMatrix = entry.pose();

        // 使用半透明渲染层
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // 橙色半透明（与灾厄印记的紫色区分）
        int red = 255;
        int green = 140;
        int blue = 0;
        int alpha = 180;

        float size = 0.4f;

        // 绘制十字线（两条交叉的矩形线段）
        drawLine(vertexConsumer, posMatrix, entry, -size, 0, size, 0, red, green, blue, alpha, light);
        drawLine(vertexConsumer, posMatrix, entry, 0, -size, 0, size, red, green, blue, alpha, light);
    }

    /**
     * 绘制一条线段（矩形近似）
     */
    private void drawLine(VertexConsumer consumer, Matrix4f posMatrix, PoseStack.Pose entry,
            float x1, float y1, float x2, float y2,
            int r, int g, int b, int a, int light) {
        // 计算线段方向的垂直向量用于生成宽度
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0)
            return;
        float nx = -dy / len * 0.06f; // 半宽度
        float ny = dx / len * 0.06f;

        // 正面
        consumer.addVertex(posMatrix, x1 + nx, y1 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        consumer.addVertex(posMatrix, x1 - nx, y1 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        consumer.addVertex(posMatrix, x2 - nx, y2 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        // 正面第二个三角形
        consumer.addVertex(posMatrix, x1 + nx, y1 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        consumer.addVertex(posMatrix, x2 - nx, y2 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        consumer.addVertex(posMatrix, x2 + nx, y2 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, 1);

        // 背面
        consumer.addVertex(posMatrix, x1 - nx, y1 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);

        consumer.addVertex(posMatrix, x1 + nx, y1 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);

        consumer.addVertex(posMatrix, x2 - nx, y2 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);

        // 背面第二个三角形
        consumer.addVertex(posMatrix, x2 - nx, y2 - ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);

        consumer.addVertex(posMatrix, x1 + nx, y1 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);

        consumer.addVertex(posMatrix, x2 + nx, y2 + ny, 0)
                .setColor(r, g, b, a)
                .setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, -1);
    }

    @Override
    public ResourceLocation getTextureLocation(TripwireTrapEntity entity) {
        return TEXTURE;
    }
}
