package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.SteelCoilEntity;

/**
 * 钢卷渲染：直接逐四边形绘制 12 边形圆柱，UV 精确取自 Blockbench 模型（128x128 图集）。
 *
 * <p>不使用方块模型 JSON（1.21.1 不允许 30 度），也不使用 ModelPart
 * （1.21.1 的 CubeListBuilder 不支持逐面 UV），因此材质严格按模型 UV 贴合。
 */
public class SteelCoilRenderer extends EntityRenderer<SteelCoilEntity> {

    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/block/steel_coil.png");
    private static final float TEX = 128.0F;

    private record Quad(float[] pos, float[] uv, float[] normal) {}

    private static final Quad[] QUADS = {
        new Quad(new float[]{-6,-7.4641,-2,6,-7.4641,-2,6,-2.4641,-2,-6,-2.4641,-2}, new float[]{0,12,12,12,12,17,0,17}, new float[]{0,0,-1}),
        new Quad(new float[]{6,-7.4641,2,-6,-7.4641,2,-6,-2.4641,2,6,-2.4641,2}, new float[]{13,0,25,0,25,5,13,5}, new float[]{0,0,1}),
        new Quad(new float[]{-6,-7.4641,2,-6,-7.4641,-2,-6,-2.4641,-2,-6,-2.4641,2}, new float[]{39,53,43,53,43,58,39,58}, new float[]{-1,0,0}),
        new Quad(new float[]{6,-7.4641,-2,6,-7.4641,2,6,-2.4641,2,6,-2.4641,-2}, new float[]{52,52,56,52,56,57,52,57}, new float[]{1,0,0}),
        new Quad(new float[]{-6,-7.4641,2,6,-7.4641,2,6,-7.4641,-2,-6,-7.4641,-2}, new float[]{51,33,39,33,39,37,51,37}, new float[]{0,-1,0}),
        new Quad(new float[]{-6,-2.4641,-2,6,-2.4641,-2,6,-2.4641,2,-6,-2.4641,2}, new float[]{51,32,39,32,39,28,51,28}, new float[]{0,1,0}),
        new Quad(new float[]{-5.995,-7.4641,2,5.995,-7.4641,2,5.995,-3.134,-0.5,-5.995,-3.134,-0.5}, new float[]{13,6,25,6,25,11,13,11}, new float[]{0,-5.5,0.4737}),
        new Quad(new float[]{5.995,-5.4641,5.4641,-5.995,-5.4641,5.4641,-5.995,-1.134,2.9641,5.995,-1.134,2.9641}, new float[]{13,12,25,12,25,17,13,17}, new float[]{0,-4.5,2.2058}),
        new Quad(new float[]{-5.995,-5.4641,5.4641,-5.995,-7.4641,2,-5.995,-3.134,-0.5,-5.995,-1.134,2.9641}, new float[]{0,57,4,57,4,62,0,62}, new float[]{-1,-5,1.3397}),
        new Quad(new float[]{5.995,-7.4641,2,5.995,-5.4641,5.4641,5.995,-1.134,2.9641,5.995,-3.134,-0.5}, new float[]{44,53,48,53,48,58,44,58}, new float[]{1,-5,1.3397}),
        new Quad(new float[]{-5.995,-5.4641,5.4641,5.995,-5.4641,5.4641,5.995,-7.4641,2,-5.995,-7.4641,2}, new float[]{12,42,0,42,0,46,12,46}, new float[]{0,-5.866,1.8397}),
        new Quad(new float[]{-5.995,-3.134,-0.5,5.995,-3.134,-0.5,5.995,-1.134,2.9641,-5.995,-1.134,2.9641}, new float[]{51,42,39,42,39,38,51,38}, new float[]{0,-4.134,0.8397}),
        new Quad(new float[]{-5.99,-2.9641,1.134,5.99,-2.9641,1.134,5.99,0.5,3.134,-5.99,0.5,3.134}, new float[]{13,42,25,42,25,46,13,46}, new float[]{0,7.5,-0.0622}),
        new Quad(new float[]{5.99,-5.4641,5.4641,-5.99,-5.4641,5.4641,-5.99,-2,7.4641,5.99,-2,7.4641}, new float[]{26,42,38,42,38,46,26,46}, new float[]{0,6.5,1.6699}),
        new Quad(new float[]{-5.99,-5.4641,5.4641,-5.99,-2.9641,1.134,-5.99,0.5,3.134,-5.99,-2,7.4641}, new float[]{11,57,16,57,16,61,11,61}, new float[]{-1,7,0.8038}),
        new Quad(new float[]{5.99,-2.9641,1.134,5.99,-5.4641,5.4641,5.99,-2,7.4641,5.99,0.5,3.134}, new float[]{5,57,10,57,10,61,5,61}, new float[]{1,7,0.8038}),
        new Quad(new float[]{-5.99,-5.4641,5.4641,5.99,-5.4641,5.4641,5.99,-2.9641,1.134,-5.99,-2.9641,1.134}, new float[]{25,18,13,18,13,23,25,23}, new float[]{0,6.134,0.3038}),
        new Quad(new float[]{-5.99,0.5,3.134,5.99,0.5,3.134,5.99,-2,7.4641,-5.99,-2,7.4641}, new float[]{12,23,0,23,0,18,12,18}, new float[]{0,7.866,1.3038}),
        new Quad(new float[]{-6,-2,2.4641,6,-2,2.4641,6,2,2.4641,-6,2,2.4641}, new float[]{39,43,51,43,51,47,39,47}, new float[]{0,0,-1}),
        new Quad(new float[]{6,-2,7.4641,-6,-2,7.4641,-6,2,7.4641,6,2,7.4641}, new float[]{0,47,12,47,12,51,0,51}, new float[]{0,0,1}),
        new Quad(new float[]{-6,-2,7.4641,-6,-2,2.4641,-6,2,2.4641,-6,2,7.4641}, new float[]{23,57,28,57,28,61,23,61}, new float[]{-1,0,0}),
        new Quad(new float[]{6,-2,2.4641,6,-2,7.4641,6,2,7.4641,6,2,2.4641}, new float[]{17,57,22,57,22,61,17,61}, new float[]{1,0,0}),
        new Quad(new float[]{-6,-2,7.4641,6,-2,7.4641,6,-2,2.4641,-6,-2,2.4641}, new float[]{25,24,13,24,13,29,25,29}, new float[]{0,-1,0}),
        new Quad(new float[]{-6,2,2.4641,6,2,2.4641,6,2,7.4641,-6,2,7.4641}, new float[]{12,29,0,29,0,24,12,24}, new float[]{0,1,0}),
        new Quad(new float[]{-5.995,-0.5,3.134,5.995,-0.5,3.134,5.995,2.9641,1.134,-5.995,2.9641,1.134}, new float[]{13,47,25,47,25,51,13,51}, new float[]{0,-6.9641,5.9378}),
        new Quad(new float[]{5.995,2,7.4641,-5.995,2,7.4641,-5.995,5.4641,5.4641,5.995,5.4641,5.4641}, new float[]{26,47,38,47,38,51,26,51}, new float[]{0,-5.9641,7.6699}),
        new Quad(new float[]{-5.995,2,7.4641,-5.995,-0.5,3.134,-5.995,2.9641,1.134,-5.995,5.4641,5.4641}, new float[]{57,40,62,40,62,44,57,44}, new float[]{-1,-6.4641,6.8038}),
        new Quad(new float[]{5.995,-0.5,3.134,5.995,2,7.4641,5.995,5.4641,5.4641,5.995,2.9641,1.134}, new float[]{29,57,34,57,34,61,29,61}, new float[]{1,-6.4641,6.8038}),
        new Quad(new float[]{-5.995,2,7.4641,5.995,2,7.4641,5.995,-0.5,3.134,-5.995,-0.5,3.134}, new float[]{38,6,26,6,26,11,38,11}, new float[]{0,-7.3301,7.3038}),
        new Quad(new float[]{-5.995,2.9641,1.134,5.995,2.9641,1.134,5.995,5.4641,5.4641,-5.995,5.4641,5.4641}, new float[]{38,5,26,5,26,0,38,0}, new float[]{0,-5.5981,6.3038}),
        new Quad(new float[]{-5.99,3.134,-0.5,5.99,3.134,-0.5,5.99,7.4641,2,-5.99,7.4641,2}, new float[]{0,0,12,0,12,5,0,5}, new float[]{0,8.9641,-5.5263}),
        new Quad(new float[]{5.99,1.134,2.9641,-5.99,1.134,2.9641,-5.99,5.4641,5.4641,5.99,5.4641,5.4641}, new float[]{0,6,12,6,12,11,0,11}, new float[]{0,7.9641,-3.7942}),
        new Quad(new float[]{-5.99,1.134,2.9641,-5.99,3.134,-0.5,-5.99,7.4641,2,-5.99,5.4641,5.4641}, new float[]{52,46,56,46,56,51,52,51}, new float[]{-1,8.4641,-4.6603}),
        new Quad(new float[]{5.99,3.134,-0.5,5.99,1.134,2.9641,5.99,5.4641,5.4641,5.99,7.4641,2}, new float[]{52,40,56,40,56,45,52,45}, new float[]{1,8.4641,-4.6603}),
        new Quad(new float[]{-5.99,1.134,2.9641,5.99,1.134,2.9641,5.99,3.134,-0.5,-5.99,3.134,-0.5}, new float[]{51,23,39,23,39,27,51,27}, new float[]{0,7.5981,-5.1603}),
        new Quad(new float[]{-5.99,7.4641,2,5.99,7.4641,2,5.99,5.4641,5.4641,-5.99,5.4641,5.4641}, new float[]{51,22,39,22,39,18,51,18}, new float[]{0,9.3301,-4.1603}),
        new Quad(new float[]{-6,2.4641,-2,6,2.4641,-2,6,7.4641,-2,-6,7.4641,-2}, new float[]{26,12,38,12,38,17,26,17}, new float[]{0,0,-1}),
        new Quad(new float[]{6,2.4641,2,-6,2.4641,2,-6,7.4641,2,6,7.4641,2}, new float[]{26,18,38,18,38,23,26,23}, new float[]{0,0,1}),
        new Quad(new float[]{-6,2.4641,2,-6,2.4641,-2,-6,7.4641,-2,-6,7.4641,2}, new float[]{57,51,61,51,61,56,57,56}, new float[]{-1,0,0}),
        new Quad(new float[]{6,2.4641,-2,6,2.4641,2,6,7.4641,2,6,7.4641,-2}, new float[]{57,45,61,45,61,50,57,50}, new float[]{1,0,0}),
        new Quad(new float[]{-6,2.4641,2,6,2.4641,2,6,2.4641,-2,-6,2.4641,-2}, new float[]{12,52,0,52,0,56,12,56}, new float[]{0,-1,0}),
        new Quad(new float[]{-6,7.4641,-2,6,7.4641,-2,6,7.4641,2,-6,7.4641,2}, new float[]{51,52,39,52,39,48,51,48}, new float[]{0,1,0}),
        new Quad(new float[]{-5.995,1.134,-2.9641,5.995,1.134,-2.9641,5.995,5.4641,-5.4641,-5.995,5.4641,-5.4641}, new float[]{26,24,38,24,38,29,26,29}, new float[]{0,-1.5,7.4019}),
        new Quad(new float[]{5.995,3.134,0.5,-5.995,3.134,0.5,-5.995,7.4641,-2,5.995,7.4641,-2}, new float[]{0,30,12,30,12,35,0,35}, new float[]{0,-0.5,9.134}),
        new Quad(new float[]{-5.995,3.134,0.5,-5.995,1.134,-2.9641,-5.995,5.4641,-5.4641,-5.995,7.4641,-2}, new float[]{49,58,53,58,53,63,49,63}, new float[]{-1,-1,8.2679}),
        new Quad(new float[]{5.995,1.134,-2.9641,5.995,3.134,0.5,5.995,7.4641,-2,5.995,5.4641,-5.4641}, new float[]{57,57,61,57,61,62,57,62}, new float[]{1,-1,8.2679}),
        new Quad(new float[]{-5.995,3.134,0.5,5.995,3.134,0.5,5.995,1.134,-2.9641,-5.995,1.134,-2.9641}, new float[]{64,5,52,5,52,9,64,9}, new float[]{0,-1.866,8.7679}),
        new Quad(new float[]{-5.995,5.4641,-5.4641,5.995,5.4641,-5.4641,5.995,7.4641,-2,-5.995,7.4641,-2}, new float[]{64,4,52,4,52,0,64,0}, new float[]{0,-0.134,7.7679}),
        new Quad(new float[]{-5.99,2,-7.4641,5.99,2,-7.4641,5.99,5.4641,-5.4641,-5.99,5.4641,-5.4641}, new float[]{52,10,64,10,64,14,52,14}, new float[]{0,3.5,-6.9904}),
        new Quad(new float[]{5.99,-0.5,-3.134,-5.99,-0.5,-3.134,-5.99,2.9641,-1.134,5.99,2.9641,-1.134}, new float[]{13,52,25,52,25,56,13,56}, new float[]{0,2.5,-5.2583}),
        new Quad(new float[]{-5.99,-0.5,-3.134,-5.99,2,-7.4641,-5.99,5.4641,-5.4641,-5.99,2.9641,-1.134}, new float[]{41,59,46,59,46,63,41,63}, new float[]{-1,3,-6.1244}),
        new Quad(new float[]{5.99,2,-7.4641,5.99,-0.5,-3.134,5.99,2.9641,-1.134,5.99,5.4641,-5.4641}, new float[]{35,59,40,59,40,63,35,63}, new float[]{1,3,-6.1244}),
        new Quad(new float[]{-5.99,-0.5,-3.134,5.99,-0.5,-3.134,5.99,2,-7.4641,-5.99,2,-7.4641}, new float[]{38,30,26,30,26,35,38,35}, new float[]{0,2.134,-6.6244}),
        new Quad(new float[]{-5.99,5.4641,-5.4641,5.99,5.4641,-5.4641,5.99,2.9641,-1.134,-5.99,2.9641,-1.134}, new float[]{25,35,13,35,13,30,25,30}, new float[]{0,3.866,-5.6244}),
        new Quad(new float[]{-6,-2,-7.4641,6,-2,-7.4641,6,2,-7.4641,-6,2,-7.4641}, new float[]{52,15,64,15,64,19,52,19}, new float[]{0,0,-1}),
        new Quad(new float[]{6,-2,-2.4641,-6,-2,-2.4641,-6,2,-2.4641,6,2,-2.4641}, new float[]{52,20,64,20,64,24,52,24}, new float[]{0,0,1}),
        new Quad(new float[]{-6,-2,-2.4641,-6,-2,-7.4641,-6,2,-7.4641,-6,2,-2.4641}, new float[]{11,62,16,62,16,66,11,66}, new float[]{-1,0,0}),
        new Quad(new float[]{6,-2,-7.4641,6,-2,-2.4641,6,2,-2.4641,6,2,-7.4641}, new float[]{5,62,10,62,10,66,5,66}, new float[]{1,0,0}),
        new Quad(new float[]{-6,-2,-2.4641,6,-2,-2.4641,6,-2,-7.4641,-6,-2,-7.4641}, new float[]{25,36,13,36,13,41,25,41}, new float[]{0,-1,0}),
        new Quad(new float[]{-6,2,-7.4641,6,2,-7.4641,6,2,-2.4641,-6,2,-2.4641}, new float[]{12,41,0,41,0,36,12,36}, new float[]{0,1,0}),
        new Quad(new float[]{-5.995,-5.4641,-5.4641,5.995,-5.4641,-5.4641,5.995,-2,-7.4641,-5.995,-2,-7.4641}, new float[]{52,25,64,25,64,29,52,29}, new float[]{0,-0.0359,1.9378}),
        new Quad(new float[]{5.995,-2.9641,-1.134,-5.995,-2.9641,-1.134,-5.995,0.5,-3.134,5.995,0.5,-3.134}, new float[]{26,52,38,52,38,56,26,56}, new float[]{0,0.9641,3.6699}),
        new Quad(new float[]{-5.995,-2.9641,-1.134,-5.995,-5.4641,-5.4641,-5.995,-2,-7.4641,-5.995,0.5,-3.134}, new float[]{23,62,28,62,28,66,23,66}, new float[]{-1,0.4641,2.8038}),
        new Quad(new float[]{5.995,-5.4641,-5.4641,5.995,-2.9641,-1.134,5.995,0.5,-3.134,5.995,-2,-7.4641}, new float[]{17,62,22,62,22,66,17,66}, new float[]{1,0.4641,2.8038}),
        new Quad(new float[]{-5.995,-2.9641,-1.134,5.995,-2.9641,-1.134,5.995,-5.4641,-5.4641,-5.995,-5.4641,-5.4641}, new float[]{51,0,39,0,39,5,51,5}, new float[]{0,-0.4019,3.3038}),
        new Quad(new float[]{-5.995,-2,-7.4641,5.995,-2,-7.4641,5.995,0.5,-3.134,-5.995,0.5,-3.134}, new float[]{38,41,26,41,26,36,38,36}, new float[]{0,1.3301,2.3038}),
        new Quad(new float[]{-5.99,-5.4641,-5.4641,5.99,-5.4641,-5.4641,5.99,-1.134,-2.9641,-5.99,-1.134,-2.9641}, new float[]{39,6,51,6,51,11,39,11}, new float[]{0,2.0359,-1.5263}),
        new Quad(new float[]{5.99,-7.4641,-2,-5.99,-7.4641,-2,-5.99,-3.134,0.5,5.99,-3.134,0.5}, new float[]{39,12,51,12,51,17,39,17}, new float[]{0,1.0359,0.2058}),
        new Quad(new float[]{-5.99,-7.4641,-2,-5.99,-5.4641,-5.4641,-5.99,-1.134,-2.9641,-5.99,-3.134,0.5}, new float[]{62,45,66,45,66,50,62,50}, new float[]{-1,1.5359,-0.6603}),
        new Quad(new float[]{5.99,-5.4641,-5.4641,5.99,-7.4641,-2,5.99,-3.134,0.5,5.99,-1.134,-2.9641}, new float[]{29,62,33,62,33,67,29,67}, new float[]{1,1.5359,-0.6603}),
        new Quad(new float[]{-5.99,-7.4641,-2,5.99,-7.4641,-2,5.99,-5.4641,-5.4641,-5.99,-5.4641,-5.4641}, new float[]{64,35,52,35,52,39,64,39}, new float[]{0,0.6699,-1.1603}),
        new Quad(new float[]{-5.99,-1.134,-2.9641,5.99,-1.134,-2.9641,5.99,-3.134,0.5,-5.99,-3.134,0.5}, new float[]{64,34,52,34,52,30,64,30}, new float[]{0,2.4019,-0.1603}),
    };

    public SteelCoilRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = SteelCoilEntity.SIZE * 0.5F;
    }

    @Override
    public void render(SteelCoilEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        matrices.pushPose();
        matrices.translate(0.0F, SteelCoilEntity.SIZE * 0.5F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(entity.getRoll() + tickDelta * entity.getRollSpeed() * 57.2958F));
        PoseStack.Pose pose = matrices.last();
        VertexConsumer c = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
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
        // 模型单位 16 = 1 格
        c.addVertex(pose, q.pos[i * 3] / 16.0F, q.pos[i * 3 + 1] / 16.0F, q.pos[i * 3 + 2] / 16.0F);
        c.setColor(255, 255, 255, 255);
        c.setUv(q.uv[i * 2] / TEX, q.uv[i * 2 + 1] / TEX);
        c.setOverlay(0, 0);
        c.setLight(light);
        c.setNormal(pose, q.normal[0], q.normal[1], q.normal[2]);
    }

    @Override
    public ResourceLocation getTextureLocation(SteelCoilEntity entity) {
        return TEXTURE;
    }
}
