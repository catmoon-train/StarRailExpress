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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 竹杆几何体渲染工具。
 * <p>
 * 直接向 {@link VertexConsumer} 写顶点，沿模型 +Y 轴（配合调用方的旋转矩阵即“朝向前方”）
 * 逐段绘制竹杆：分段 + 竹节 + 尖头，避免物品模型被拉伸变形。
 */
@Environment(EnvType.CLIENT)
public final class BambooPoleGeometry {

    private static final ResourceLocation STALK = ResourceLocation.withDefaultNamespace("block/bamboo_stalk");

    /** 单节竹杆长度，贴图按节重复，避免长杆被拉伸模糊。 */
    private static final float SEGMENT = 0.85F;
    /** 竹节环高度。 */
    private static final float NODE_HEIGHT = 0.07F;
    /** 竹节环相对杆身的加粗量。 */
    private static final float NODE_EXPAND = 0.028F;

    private BambooPoleGeometry() {
    }

    /**
     * 把模型空间的 +Y 轴对齐到给定方向：之后的绘制沿 {@code dir} 向前延伸。
     * 直接构造正交基矩阵，避免逐轴旋转在不同朝向下的歧义。
     */
    public static void orient(PoseStack poseStack, Vec3 rawDir) {
        Vec3 dir = rawDir.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : rawDir.normalize();
        Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();
        poseStack.mulPose(new Matrix4f(
                (float) right.x, (float) dir.x, (float) up.x, 0F,
                (float) right.y, (float) dir.y, (float) up.y, 0F,
                (float) right.z, (float) dir.z, (float) up.z, 0F,
                0F, 0F, 0F, 1F));
    }

    /**
     * 在当前的 PoseStack 变换下绘制一段竹杆。
     *
     * @param from      起点（模型 +Y 方向上的坐标）
     * @param to        终点（含尖头）
     * @param radius    杆身半宽
     * @param tipLength 尖头长度（会被限制为总长的一部分）
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, int light, float from, float to,
            float radius, float tipLength) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(STALK);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        render(poseStack, consumer, sprite, light, from, to, radius, tipLength);
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
            float from, float to, float radius, float tipLength) {
        float total = to - from;
        if (total <= 0.02F) {
            return;
        }
        float tip = Math.min(tipLength, total * 0.45F);
        float bodyEnd = to - tip;

        // 均分竹节，保证每节长度接近 SEGMENT，贴图不会被压缩/拉伸
        float bodyLength = bodyEnd - from;
        int segments = Math.max(1, Math.round(bodyLength / SEGMENT));
        float segment = bodyLength / segments;
        for (int i = 0; i < segments; i++) {
            float y = from + segment * i;
            prism(poseStack, consumer, sprite, light, y, y + segment, radius);
            // 竹节：只在两节之间加环
            if (i < segments - 1) {
                float joint = y + segment;
                prism(poseStack, consumer, sprite, light, joint - NODE_HEIGHT * 0.5F, joint + NODE_HEIGHT * 0.5F,
                        radius + NODE_EXPAND);
            }
        }
        if (tip > 0.02F) {
            tip(poseStack, consumer, sprite, light, bodyEnd, to, radius);
        }
    }

    /** 方形棱柱（四侧面），沿 Y 轴从 y0 到 y1。 */
    private static void prism(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
            float y0, float y1, float r) {
        float ub = sprite.getU0();
        float ue = sprite.getU1();
        float vb = sprite.getV1();
        float vt = sprite.getV0();
        // north (-Z)
        quad(poseStack, consumer, light, -r, y0, -r, r, y0, -r, r, y1, -r, -r, y1, -r, ub, ue, vb, vt, 0F, 0F, -1F);
        // east (+X)
        quad(poseStack, consumer, light, r, y0, -r, r, y0, r, r, y1, r, r, y1, -r, ub, ue, vb, vt, 1F, 0F, 0F);
        // south (+Z)
        quad(poseStack, consumer, light, r, y0, r, -r, y0, r, -r, y1, r, r, y1, r, ub, ue, vb, vt, 0F, 0F, 1F);
        // west (-X)
        quad(poseStack, consumer, light, -r, y0, r, -r, y0, -r, -r, y1, -r, -r, y1, r, ub, ue, vb, vt, -1F, 0F, 0F);
    }

    /** 四棱锥尖头。 */
    private static void tip(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int light,
            float y0, float y1, float r) {
        float ub = sprite.getU0();
        float ue = sprite.getU1();
        float um = (ub + ue) * 0.5F;
        float vb = sprite.getV1();
        float vt = sprite.getV0();
        // north (-Z)
        tri(poseStack, consumer, light, -r, y0, -r, r, y0, -r, 0F, y1, 0F, ub, ue, um, vb, vt, 0F, 0F, -1F);
        // east (+X)
        tri(poseStack, consumer, light, r, y0, -r, r, y0, r, 0F, y1, 0F, ub, ue, um, vb, vt, 1F, 0F, 0F);
        // south (+Z)
        tri(poseStack, consumer, light, r, y0, r, -r, y0, r, 0F, y1, 0F, ub, ue, um, vb, vt, 0F, 0F, 1F);
        // west (-X)
        tri(poseStack, consumer, light, -r, y0, r, -r, y0, -r, 0F, y1, 0F, ub, ue, um, vb, vt, -1F, 0F, 0F);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer, int light, float ax, float ay, float az,
            float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float u0,
            float u1, float vBottom, float vTop, float nx, float ny, float nz) {
        vertex(poseStack, consumer, light, ax, ay, az, u0, vBottom, nx, ny, nz);
        vertex(poseStack, consumer, light, bx, by, bz, u1, vBottom, nx, ny, nz);
        vertex(poseStack, consumer, light, cx, cy, cz, u1, vTop, nx, ny, nz);
        vertex(poseStack, consumer, light, dx, dy, dz, u0, vTop, nx, ny, nz);
    }

    /** 三角形（以退化四边形写入，RenderType 为 QUADS 模式）。 */
    private static void tri(PoseStack poseStack, VertexConsumer consumer, int light, float ax, float ay, float az,
            float bx, float by, float bz, float cx, float cy, float cz, float u0, float u1, float um, float vBottom,
            float vTop, float nx, float ny, float nz) {
        vertex(poseStack, consumer, light, ax, ay, az, u0, vBottom, nx, ny, nz);
        vertex(poseStack, consumer, light, bx, by, bz, u1, vBottom, nx, ny, nz);
        vertex(poseStack, consumer, light, cx, cy, cz, um, vTop, nx, ny, nz);
        vertex(poseStack, consumer, light, cx, cy, cz, um, vTop, nx, ny, nz);
    }

    private static void vertex(PoseStack poseStack, VertexConsumer consumer, int light, float x, float y, float z,
            float u, float v, float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
