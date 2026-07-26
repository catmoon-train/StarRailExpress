package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * 球体网格渲染工具：以 UV 球（纬度×经度四边形）方式向 {@link VertexConsumer} 写入顶点。
 * 供幽露锚点 / 球烟等球形实体渲染器复用（顶点格式与 {@code RenderType.entityTranslucent} 兼容）。
 */
@Environment(EnvType.CLIENT)
public final class SphereRenderHelper {

    private SphereRenderHelper() {
    }

    /**
     * 在当前 PoseStack 原点渲染一个球体。
     *
     * @param latSegments 纬度分段（≥4）
     * @param lonSegments 经度分段（≥8）
     */
    public static void renderSphere(PoseStack poseStack, VertexConsumer consumer, float radius,
            int latSegments, int lonSegments, int r, int g, int b, int a, int light) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        for (int lat = 0; lat < latSegments; lat++) {
            double theta0 = Math.PI * lat / latSegments;
            double theta1 = Math.PI * (lat + 1) / latSegments;
            for (int lon = 0; lon < lonSegments; lon++) {
                double phi0 = 2 * Math.PI * lon / lonSegments;
                double phi1 = 2 * Math.PI * (lon + 1) / lonSegments;

                // 四边形四角（两三角形合并为 quad：entityTranslucent 为 QUADS 模式）
                float[] p00 = point(radius, theta0, phi0);
                float[] p01 = point(radius, theta0, phi1);
                float[] p10 = point(radius, theta1, phi0);
                float[] p11 = point(radius, theta1, phi1);

                vertex(consumer, matrix, pose, p00, radius, r, g, b, a, light);
                vertex(consumer, matrix, pose, p10, radius, r, g, b, a, light);
                vertex(consumer, matrix, pose, p11, radius, r, g, b, a, light);
                vertex(consumer, matrix, pose, p01, radius, r, g, b, a, light);
            }
        }
    }

    private static float[] point(float radius, double theta, double phi) {
        return new float[] {
                (float) (radius * Math.sin(theta) * Math.cos(phi)),
                (float) (radius * Math.cos(theta)),
                (float) (radius * Math.sin(theta) * Math.sin(phi))
        };
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
            float[] p, float radius, int r, int g, int b, int a, int light) {
        // 球面法线 = 顶点方向
        float nx = p[0] / radius, ny = p[1] / radius, nz = p[2] / radius;
        consumer.addVertex(matrix, p[0], p[1], p[2])
                .setColor(r, g, b, a)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
