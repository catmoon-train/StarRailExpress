package io.wifi.starrailexpress.client.model.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class CustomPlayerPlushModel {
    private static final float TEX_SIZE = 64f;
    private record FaceUV(Direction dir, float u0, float v0, float u1, float v1, int rotation) {
    }

    private record Elem(float x0, float y0, float z0,
            float x1, float y1, float z1,
            float pivotX, float pivotY, float pivotZ,
            Direction.Axis axis, float angleDeg,
            FaceUV[] faces) {
    }

    public CustomPlayerPlushModel(ModelPart bakeLayer) {
    }

    // Blockbench 的 uv 单位是按 16 格算的，texture_size=64，所以要 *4 换成真实像素坐标
    private static FaceUV f(Direction dir, float u0, float v0, float u1, float v1, int rot) {
        return new FaceUV(dir, u0 * 4f, v0 * 4f, u1 * 4f, v1 * 4f, rot);
    }

    private static Elem e(float x0, float y0, float z0, float x1, float y1, float z1,
            Float pivotX, Float pivotY, Float pivotZ,
            Direction.Axis axis, float angle, FaceUV... faces) {
        return new Elem(x0, y0, z0, x1, y1, z1,
                pivotX == null ? 0 : pivotX, pivotY == null ? 0 : pivotY, pivotZ == null ? 0 : pivotZ,
                axis, angle, faces);
    }

    private static final Elem[] ELEMENTS = new Elem[] {
            // 0: bottom
            e(3.4f, -0.1f, 7f, 12.6f, 2.1f, 12.45f, null, null, null, null, 0f,
                    f(Direction.NORTH, 5, 7, 7, 8, 0),
                    f(Direction.EAST, 4, 7, 5, 8, 0),
                    f(Direction.SOUTH, 8, 7, 10, 8, 0),
                    f(Direction.WEST, 7, 7, 8, 8, 0),
                    f(Direction.UP, 5, 7, 7, 8, 0),
                    f(Direction.DOWN, 5, 7, 7, 8, 0)),
            // 1: right_leg part
            e(9.01f, 0.01f, 5.01f, 10.99f, 1.99f, 7.99f, 10f, 0f, 7f, Direction.Axis.Y, -22.5f,
                    f(Direction.NORTH, 1, 4, 2, 5, 0),
                    f(Direction.EAST, 0, 5, 1, 8, 270),
                    f(Direction.SOUTH, 2, 4, 3, 5, 0),
                    f(Direction.WEST, 2, 5, 3, 8, 270),
                    f(Direction.UP, 1, 5, 2, 8, 180),
                    f(Direction.DOWN, 3, 5, 4, 8, 0)),
            // 2: right_leg part
            e(9f, 0f, 5f, 11f, 2f, 8f, 10f, 0f, 7f, Direction.Axis.Y, -22.5f,
                    f(Direction.NORTH, 1, 8, 2, 9, 0),
                    f(Direction.EAST, 0, 9, 1, 12, 270),
                    f(Direction.SOUTH, 2, 8, 3, 9, 0),
                    f(Direction.WEST, 2, 9, 3, 12, 270),
                    f(Direction.UP, 1, 9, 2, 12, 180),
                    f(Direction.DOWN, 3, 9, 4, 12, 0)),
            // 3: right_leg part
            e(8.5f, 0f, 3f, 11.5f, 3f, 5f, 10f, 0f, 7f, Direction.Axis.Y, -22.5f,
                    f(Direction.NORTH, 1, 4, 2, 5, 0),
                    f(Direction.EAST, 2, 7.25f, 3, 8, 270),
                    f(Direction.SOUTH, 2, 4, 3, 5, 0),
                    f(Direction.WEST, 0, 7.25f, 1, 8, 90),
                    f(Direction.UP, 1, 7.25f, 2, 8, 180),
                    f(Direction.DOWN, 3, 7.25f, 4, 8, 0)),
            // 4: left_leg part
            e(5.01f, 0.01f, 5.01f, 6.99f, 1.99f, 7.99f, 6f, 0f, 7f, Direction.Axis.Y, 22.5f,
                    f(Direction.NORTH, 5, 12, 6, 13, 0),
                    f(Direction.EAST, 4, 13, 5, 16, 270),
                    f(Direction.SOUTH, 6, 12, 7, 13, 0),
                    f(Direction.WEST, 6, 13, 7, 16, 270),
                    f(Direction.UP, 5, 13, 6, 16, 180),
                    f(Direction.DOWN, 7, 13, 8, 16, 0)),
            // 5: left_leg part
            e(5f, 0f, 5f, 7f, 2f, 8f, 6f, 0f, 7f, Direction.Axis.Y, 22.5f,
                    f(Direction.NORTH, 1, 12, 2, 13, 0),
                    f(Direction.EAST, 0, 13, 1, 16, 270),
                    f(Direction.SOUTH, 2, 12, 3, 13, 0),
                    f(Direction.WEST, 2, 13, 3, 16, 270),
                    f(Direction.UP, 1, 13, 2, 16, 180),
                    f(Direction.DOWN, 3, 13, 4, 16, 0)),
            // 6: left_leg part
            e(4.5f, 0f, 3f, 7.5f, 3f, 5f, 6f, 0f, 7f, Direction.Axis.Y, 22.5f,
                    f(Direction.NORTH, 5, 12, 6, 13, 0),
                    f(Direction.EAST, 6, 15.25f, 7, 16, 270),
                    f(Direction.SOUTH, 6, 12, 7, 13, 0),
                    f(Direction.WEST, 4, 15.25f, 5, 16, 90),
                    f(Direction.UP, 5, 15.25f, 6, 16, 180),
                    f(Direction.DOWN, 7, 15.25f, 8, 16, 0)),
            // 7: right_arm
            e(9.9f, 3.9f, 8.4f, 15.1f, 6.1f, 10.6f, 11f, 5f, 9.5f, Direction.Axis.Z, -22.5f,
                    f(Direction.NORTH, 11, 5, 11.75f, 8, 90),
                    f(Direction.EAST, 11.75f, 4, 12.5f, 5, 0),
                    f(Direction.SOUTH, 12.5f, 5, 13.5f, 8, 270),
                    f(Direction.WEST, 11, 4, 11.75f, 5, 0),
                    f(Direction.UP, 11.75f, 5, 12.5f, 8, 270),
                    f(Direction.DOWN, 10, 5, 11, 8, 270)),
            // 8: left_arm
            e(0.9f, 3.9f, 8.4f, 6.1f, 6.1f, 10.6f, 5f, 5f, 9.5f, Direction.Axis.Z, 22.5f,
                    f(Direction.NORTH, 9, 13, 9.75f, 16, 270),
                    f(Direction.EAST, 9, 12, 9.75f, 13, 0),
                    f(Direction.SOUTH, 10.5f, 13, 11.5f, 16, 90),
                    f(Direction.WEST, 9.75f, 12, 10.5f, 13, 0),
                    f(Direction.UP, 9.75f, 13, 10.5f, 16, 90),
                    f(Direction.DOWN, 8, 13, 9, 16, 90)),
            // 9: body inside_cube
            e(5.5f, 0.25f, 7.5f, 10.5f, 6.25f, 11.5f, null, null, null, null, 0f,
                    f(Direction.NORTH, 5, 5, 7, 8, 0),
                    f(Direction.EAST, 4, 5, 5, 8, 0),
                    f(Direction.SOUTH, 8, 5, 10, 8, 0),
                    f(Direction.WEST, 7, 5, 8, 8, 0),
                    f(Direction.UP, 5, 4, 7, 5, 0),
                    f(Direction.DOWN, 7, 4, 9, 5, 0)),
            // 10: body outside_cube
            e(5.25f, 0f, 7.25f, 10.75f, 6.5f, 11.75f, null, null, null, null, 0f,
                    f(Direction.NORTH, 5, 9, 7, 12, 0),
                    f(Direction.EAST, 4, 9, 5, 12, 0),
                    f(Direction.SOUTH, 8, 9, 10, 12, 0),
                    f(Direction.WEST, 7, 9, 8, 12, 0),
                    f(Direction.UP, 5, 8, 7, 9, 0),
                    f(Direction.DOWN, 7, 8, 9, 9, 0)),
            // 11: head inside_cube
            e(3.5f, 6f, 5f, 12.5f, 15f, 14f, 8f, 6f, 9f, Direction.Axis.Y, 0f,
                    f(Direction.NORTH, 2, 2, 4, 4, 0),
                    f(Direction.EAST, 0, 2, 2, 4, 0),
                    f(Direction.SOUTH, 6, 2, 8, 4, 0),
                    f(Direction.WEST, 4, 2, 6, 4, 0),
                    f(Direction.UP, 2, 0, 4, 2, 180),
                    f(Direction.DOWN, 4, 0, 6, 2, 180)),
            // 12: head outside_cube
            e(3.25f, 5.75f, 4.75f, 12.75f, 15.25f, 14.25f, 8f, 6f, 9f, Direction.Axis.Y, 0f,
                    f(Direction.NORTH, 10, 2, 12, 4, 0),
                    f(Direction.EAST, 8, 2, 10, 4, 0),
                    f(Direction.SOUTH, 14, 2, 16, 4, 0),
                    f(Direction.WEST, 12, 2, 14, 4, 0),
                    f(Direction.UP, 10, 0, 12, 2, 180),
                    f(Direction.DOWN, 12, 0, 14, 2, 180)),
    };

    /** 在 BlockEntityRenderer#render 里调用 */
    public static void render(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        for (Elem el : ELEMENTS) {
            poseStack.pushPose();
            if (el.axis() != null && el.angleDeg() != 0f) {
                poseStack.translate(el.pivotX() / 16f, el.pivotY() / 16f, el.pivotZ() / 16f);
                switch (el.axis()) {
                    case X -> poseStack.mulPose(Axis.XP.rotationDegrees(el.angleDeg()));
                    case Y -> poseStack.mulPose(Axis.YP.rotationDegrees(el.angleDeg()));
                    case Z -> poseStack.mulPose(Axis.ZP.rotationDegrees(el.angleDeg()));
                }
                poseStack.translate(-el.pivotX() / 16f, -el.pivotY() / 16f, -el.pivotZ() / 16f);
            }
            renderElement(poseStack, buffer, el, light, overlay);
            poseStack.popPose();
        }
    }

    private static void renderElement(PoseStack poseStack, VertexConsumer buffer, Elem el, int light, int overlay) {
        float x0 = el.x0() / 16f, y0 = el.y0() / 16f, z0 = el.z0() / 16f;
        float x1 = el.x1() / 16f, y1 = el.y1() / 16f, z1 = el.z1() / 16f;

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMat = poseStack.last().normal();

        for (FaceUV face : el.faces()) {
            float[][] pos = cornersFor(face.dir(), x0, y0, z0, x1, y1, z1);
            float[] n = normalFor(face.dir());
            float[][] uv = uvFor(face);

            Vector3f norm = new Vector3f(n[0], n[1], n[2]).mul(normalMat);

            for (int i = 0; i < 4; i++) {
                Vector3f v = new Vector3f(pos[i][0], pos[i][1], pos[i][2]).mulPosition(pose);
                buffer.addVertex(v.x(), v.y(), v.z())
                        .setColor(1f, 1f, 1f, 1f)
                        .setUv(uv[i][0] / TEX_SIZE, uv[i][1] / TEX_SIZE)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(norm.x(), norm.y(), norm.z());
            }
        }
    }

    private static float[][] cornersFor(Direction dir, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (dir) {
            case NORTH -> new float[][] { { x1, y1, z0 }, { x0, y1, z0 }, { x0, y0, z0 }, { x1, y0, z0 } };
            case SOUTH -> new float[][] { { x0, y1, z1 }, { x1, y1, z1 }, { x1, y0, z1 }, { x0, y0, z1 } };
            case EAST -> new float[][] { { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 }, { x1, y0, z0 } };
            case WEST -> new float[][] { { x0, y1, z1 }, { x0, y1, z0 }, { x0, y0, z0 }, { x0, y0, z1 } };
            case UP -> new float[][] { { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
            case DOWN -> new float[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x0, y0, z0 } };
        };
    }

    private static float[] normalFor(Direction dir) {
        return switch (dir) {
            case NORTH -> new float[] { 0, 0, -1 };
            case SOUTH -> new float[] { 0, 0, 1 };
            case EAST -> new float[] { 1, 0, 0 };
            case WEST -> new float[] { -1, 0, 0 };
            case UP -> new float[] { 0, 1, 0 };
            case DOWN -> new float[] { 0, -1, 0 };
        };
    }

    private static float[][] uvFor(FaceUV face) {
        float[][] base = {
                { face.u0(), face.v0() }, // TL
                { face.u1(), face.v0() }, // TR
                { face.u1(), face.v1() }, // BR
                { face.u0(), face.v1() }, // BL
        };
        int shift = (face.rotation() / 90) % 4;
        float[][] out = new float[4][2];
        for (int i = 0; i < 4; i++)
            out[i] = base[(i + shift) % 4];
        return out;
    }
}
