package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.SteelCoilEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 钢卷渲染：直接由 Blockbench 模型的 12 个方块定义生成四边形并逐顶点贴图。
 *
 * <p>不使用方块模型 JSON（1.21.1 里面的旋转只能是 22.5° 的倍数，而模型用的是 30°），
 * 也不使用 ModelPart（1.21.1 的 CubeListBuilder 不支持逐面 UV）。因此这里把模型数据原样搬过来：
 * 每个方块的 from/to、绕 X 轴的旋转与轴心、以及六个面的 UV 矩形，运行时按与 Blockbench 网格
 * 完全相同的角点顺序生成面——顶点和 UV 出自同一份数据，不会再出现「面和贴图错位」。
 *
 * <p>尺寸：模型像素宽度 12、外径 14.9282，绘制时分别换算到实体声明的 SIZE，
 * 于是钢卷的可见体积与碰撞箱一致（SIZE×SIZE×SIZE），滚动半径 1 格也与客户端动画换算一致。
 */
public class SteelCoilRenderer extends EntityRenderer<SteelCoilEntity> {

    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/block/steel_coil.png");
    /** 图集分辨率（Blockbench 项目为 128×128）。 */
    private static final float TEX = 128.0F;

    /** 模型轴向宽度（像素）。 */
    private static final float MODEL_WIDTH = 12.0F;
    /** 模型外径（像素）：12 边形包围盒的跨度 14.9282。 */
    private static final float MODEL_DIAMETER = 14.9282F;
    /** 模型几何中心，用来把圆柱轴线挪到渲染原点。 */
    private static final float CENTER_X = 8.0F;
    private static final float CENTER_Y = 7.4641F;
    private static final float CENTER_Z = 8.0F;
    /** 像素 → 格：轴向按宽度换算，保证总宽 = SIZE。 */
    private static final float SCALE_X = SteelCoilEntity.SIZE / MODEL_WIDTH;
    /** 像素 → 格：截面按外径换算，保证直径 = SIZE（即滚动半径 = SIZE / 2）。 */
    private static final float SCALE_R = SteelCoilEntity.SIZE / MODEL_DIAMETER;

    /** 一个待绘制的四边形：pos 已换算成格并居中，uv 为图集像素坐标。 */
    private record Quad(float[] pos, float[] uv, float[] normal) {
    }

    /** 面的角点选择：每个角用 0/1 表示取 from 还是 to；UV 索引同理取 u1/u2、v1/v2。 */
    private record Face(int[][] corners, int[][] uvIndex) {
    }

    /** 一个钢卷块：尺寸、绕 X 轴旋转（度）与轴心，以及 6 个面的 UV 矩形。 */
    private record Cube(float x1, float y1, float z1, float x2, float y2, float z2,
            float rotX, float pivotY, float pivotZ, float[][] uvs) {
    }

    // 角点顺序取自 Blockbench 网格本身（每个面按「左右 / 上下交叉」排列），
    // UV 依次取矩形四角 (u1v1)(u2v1)(u1v2)(u2v2)，与模型显示完全一致。
    private static final Face NORTH = new Face(
            new int[][] { { 1, 1, 0 }, { 0, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });
    private static final Face SOUTH = new Face(
            new int[][] { { 0, 1, 1 }, { 1, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });
    private static final Face WEST = new Face(
            new int[][] { { 0, 1, 0 }, { 0, 1, 1 }, { 0, 0, 0 }, { 0, 0, 1 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });
    private static final Face EAST = new Face(
            new int[][] { { 1, 1, 1 }, { 1, 1, 0 }, { 1, 0, 1 }, { 1, 0, 0 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });
    private static final Face UP = new Face(
            new int[][] { { 0, 1, 0 }, { 1, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });
    private static final Face DOWN = new Face(
            new int[][] { { 0, 0, 1 }, { 1, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 } },
            new int[][] { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 } });

    /** 面顺序与 {@link Cube#uvs()} 一一对应：北、南、西、东、上、下。 */
    private static final Face[] FACES = { NORTH, SOUTH, WEST, EAST, UP, DOWN };

    /** 12 个方块拼成的 12 边形钢卷，数据直接来自 Blockbench 项目。 */
    private static final Cube[] CUBES = {
            new Cube(2.0F, 0.0F, 6.0F, 14.0F, 5.0F, 10.0F, 0.0F, 0.0F, 6.0F, new float[][] {
                    { 0, 12, 12, 17 }, { 13, 0, 25, 5 }, { 39, 53, 43, 58 },
                    { 52, 52, 56, 57 }, { 51, 32, 39, 28 }, { 51, 33, 39, 37 } }),
            new Cube(2.005F, 0.0F, 10.0F, 13.995F, 5.0F, 14.0F, -30.0F, 0.0F, 10.0F, new float[][] {
                    { 13, 6, 25, 11 }, { 13, 12, 25, 17 }, { 0, 57, 4, 62 },
                    { 44, 53, 48, 58 }, { 51, 42, 39, 38 }, { 12, 42, 0, 46 } }),
            new Cube(2.01F, 2.0F, 8.4641F, 13.99F, 6.0F, 13.4641F, 30.0F, 2.0F, 13.4641F, new float[][] {
                    { 13, 42, 25, 46 }, { 26, 42, 38, 46 }, { 11, 57, 16, 61 },
                    { 5, 57, 10, 61 }, { 12, 23, 0, 18 }, { 25, 18, 13, 23 } }),
            new Cube(2.0F, 5.4641F, 10.4641F, 14.0F, 9.4641F, 15.4641F, 0.0F, 5.4641F, 15.4641F,
                    new float[][] {
                            { 39, 43, 51, 47 }, { 0, 47, 12, 51 }, { 23, 57, 28, 61 },
                            { 17, 57, 22, 61 }, { 12, 29, 0, 24 }, { 25, 24, 13, 29 } }),
            new Cube(2.005F, 9.4641F, 10.4641F, 13.995F, 13.4641F, 15.4641F, -30.0F, 9.4641F, 15.4641F,
                    new float[][] {
                            { 13, 47, 25, 51 }, { 26, 47, 38, 51 }, { 57, 40, 62, 44 },
                            { 29, 57, 34, 61 }, { 38, 5, 26, 0 }, { 38, 6, 26, 11 } }),
            new Cube(2.01F, 7.9282F, 9.4641F, 13.99F, 12.9282F, 13.4641F, 30.0F, 12.9282F, 13.4641F,
                    new float[][] {
                            { 0, 0, 12, 5 }, { 0, 6, 12, 11 }, { 52, 46, 56, 51 },
                            { 52, 40, 56, 45 }, { 51, 22, 39, 18 }, { 51, 23, 39, 27 } }),
            new Cube(2.0F, 9.9282F, 6.0F, 14.0F, 14.9282F, 10.0F, 0.0F, 14.9282F, 10.0F, new float[][] {
                    { 26, 12, 38, 17 }, { 26, 18, 38, 23 }, { 57, 51, 61, 56 },
                    { 57, 45, 61, 50 }, { 51, 52, 39, 48 }, { 12, 52, 0, 56 } }),
            new Cube(2.005F, 9.9282F, 2.0F, 13.995F, 14.9282F, 6.0F, -30.0F, 14.9282F, 6.0F, new float[][] {
                    { 26, 24, 38, 29 }, { 0, 30, 12, 35 }, { 49, 58, 53, 63 },
                    { 57, 57, 61, 62 }, { 64, 4, 52, 0 }, { 64, 5, 52, 9 } }),
            new Cube(2.01F, 8.9282F, 2.5359F, 13.99F, 12.9282F, 7.5359F, 30.0F, 12.9282F, 2.5359F,
                    new float[][] {
                            { 52, 10, 64, 14 }, { 13, 52, 25, 56 }, { 41, 59, 46, 63 },
                            { 35, 59, 40, 63 }, { 25, 35, 13, 30 }, { 38, 30, 26, 35 } }),
            new Cube(2.0F, 5.4641F, 0.5359F, 14.0F, 9.4641F, 5.5359F, 0.0F, 9.4641F, 0.5359F,
                    new float[][] {
                            { 52, 15, 64, 19 }, { 52, 20, 64, 24 }, { 11, 62, 16, 66 },
                            { 5, 62, 10, 66 }, { 12, 41, 0, 36 }, { 25, 36, 13, 41 } }),
            new Cube(2.005F, 1.4641F, 0.5359F, 13.995F, 5.4641F, 5.5359F, -30.0F, 5.4641F, 0.5359F,
                    new float[][] {
                            { 52, 25, 64, 29 }, { 26, 52, 38, 56 }, { 23, 62, 28, 66 },
                            { 17, 62, 22, 66 }, { 38, 41, 26, 36 }, { 51, 0, 39, 5 } }),
            new Cube(2.01F, 2.0F, 2.5359F, 13.99F, 7.0F, 6.5359F, 30.0F, 2.0F, 2.5359F, new float[][] {
                    { 39, 6, 51, 11 }, { 39, 12, 51, 17 }, { 62, 45, 66, 50 },
                    { 29, 62, 33, 67 }, { 64, 34, 52, 30 }, { 64, 35, 52, 39 } }),
    };

    private static final Quad[] QUADS = build();

    public SteelCoilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = SteelCoilEntity.SIZE * 0.5F;
    }

    private static Quad[] build() {
        List<Quad> quads = new ArrayList<>(CUBES.length * FACES.length);
        for (Cube cube : CUBES) {
            double rad = Math.toRadians(cube.rotX);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            for (int f = 0; f < FACES.length; f++) {
                Face face = FACES[f];
                float[] rect = cube.uvs[f];
                float[] pos = new float[12];
                float[] uv = new float[8];
                for (int i = 0; i < 4; i++) {
                    float px = face.corners[i][0] == 0 ? cube.x1 : cube.x2;
                    float py = face.corners[i][1] == 0 ? cube.y1 : cube.y2;
                    float pz = face.corners[i][2] == 0 ? cube.z1 : cube.z2;
                    // 模型的旋转只有绕 X 轴，轴心为 origin：先绕轴心旋转，再平移到几何中心并缩放
                    double dy = py - cube.pivotY;
                    double dz = pz - cube.pivotZ;
                    float ry = (float) (cube.pivotY + dy * cos - dz * sin);
                    float rz = (float) (cube.pivotZ + dy * sin + dz * cos);
                    pos[i * 3] = (px - CENTER_X) * SCALE_X;
                    pos[i * 3 + 1] = (ry - CENTER_Y) * SCALE_R;
                    pos[i * 3 + 2] = (rz - CENTER_Z) * SCALE_R;
                    uv[i * 2] = face.uvIndex[i][0] == 0 ? rect[0] : rect[2];
                    uv[i * 2 + 1] = face.uvIndex[i][1] == 0 ? rect[1] : rect[3];
                }
                quads.add(new Quad(pos, uv, normal(pos)));
            }
        }
        return quads.toArray(new Quad[0]);
    }

    /** 端面沿轴向、柱面沿径向：与模型一致的平滑法线，让 12 边形看起来是圆筒。 */
    private static float[] normal(float[] pos) {
        float ax = pos[3] - pos[0];
        float ay = pos[4] - pos[1];
        float az = pos[5] - pos[2];
        float bx = pos[6] - pos[0];
        float by = pos[7] - pos[1];
        float bz = pos[8] - pos[2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-5F) {
            return new float[] { 0.0F, 1.0F, 0.0F };
        }
        nx /= len;
        ny /= len;
        nz /= len;
        if (Math.abs(nx) > 0.5F) {
            return new float[] { nx > 0.0F ? 1.0F : -1.0F, 0.0F, 0.0F };
        }
        float cy = (pos[1] + pos[4] + pos[7] + pos[10]) * 0.25F;
        float cz = (pos[2] + pos[5] + pos[8] + pos[11]) * 0.25F;
        float rl = (float) Math.sqrt(cy * cy + cz * cz);
        if (rl < 1.0E-5F) {
            return new float[] { 0.0F, 1.0F, 0.0F };
        }
        return new float[] { 0.0F, cy / rl, cz / rl };
    }

    @Override
    public void render(SteelCoilEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0F, SteelCoilEntity.SIZE * 0.5F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getRoll() + tickDelta * entity.getRollSpeed() * 57.2958F));
        PoseStack.Pose pose = matrices.last();
        // 双面渲染：模型是手工拼的 12 边形，避免任何面的绕序问题导致缺面
        VertexConsumer c = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (Quad q : QUADS) {
            emit(c, pose, q, 0, light);
            emit(c, pose, q, 1, light);
            emit(c, pose, q, 2, light);
            emit(c, pose, q, 0, light);
            emit(c, pose, q, 2, light);
            emit(c, pose, q, 3, light);
        }
        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    private static void emit(VertexConsumer c, PoseStack.Pose pose, Quad q, int i, int light) {
        c.addVertex(pose, q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]);
        c.setColor(255, 255, 255, 255);
        c.setUv(q.uv[i * 2] / TEX, q.uv[i * 2 + 1] / TEX);
        c.setOverlay(OverlayTexture.NO_OVERLAY);
        c.setLight(light);
        c.setNormal(pose, q.normal[0], q.normal[1], q.normal[2]);
    }

    @Override
    public ResourceLocation getTextureLocation(SteelCoilEntity entity) {
        return TEXTURE;
    }
}
