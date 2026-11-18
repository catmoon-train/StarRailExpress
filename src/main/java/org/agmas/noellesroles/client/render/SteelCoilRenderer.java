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
        new Quad(new float[]{-6f,-7.4641f,-2f,6f,-7.4641f,-2f,6f,-2.4641f,-2f,-6f,-2.4641f,-2f}, new float[]{0f,12f,12f,12f,12f,17f,0f,17f}, new float[]{0f,0f,-1f}),
        new Quad(new float[]{6f,-7.4641f,2f,-6f,-7.4641f,2f,-6f,-2.4641f,2f,6f,-2.4641f,2f}, new float[]{13f,0f,25f,0f,25f,5f,13f,5f}, new float[]{0f,0f,1f}),
        new Quad(new float[]{-6f,-7.4641f,2f,-6f,-7.4641f,-2f,-6f,-2.4641f,-2f,-6f,-2.4641f,2f}, new float[]{39f,53f,43f,53f,43f,58f,39f,58f}, new float[]{-1f,0f,0f}),
        new Quad(new float[]{6f,-7.4641f,-2f,6f,-7.4641f,2f,6f,-2.4641f,2f,6f,-2.4641f,-2f}, new float[]{52f,52f,56f,52f,56f,57f,52f,57f}, new float[]{1f,0f,0f}),
        new Quad(new float[]{-6f,-7.4641f,2f,6f,-7.4641f,2f,6f,-7.4641f,-2f,-6f,-7.4641f,-2f}, new float[]{51f,33f,39f,33f,39f,37f,51f,37f}, new float[]{0f,-1f,0f}),
        new Quad(new float[]{-6f,-2.4641f,-2f,6f,-2.4641f,-2f,6f,-2.4641f,2f,-6f,-2.4641f,2f}, new float[]{51f,32f,39f,32f,39f,28f,51f,28f}, new float[]{0f,1f,0f}),
        new Quad(new float[]{-5.995f,-7.4641f,2f,5.995f,-7.4641f,2f,5.995f,-3.134f,-0.5f,-5.995f,-3.134f,-0.5f}, new float[]{13f,6f,25f,6f,25f,11f,13f,11f}, new float[]{0f,-5.5f,0.4737f}),
        new Quad(new float[]{5.995f,-5.4641f,5.4641f,-5.995f,-5.4641f,5.4641f,-5.995f,-1.134f,2.9641f,5.995f,-1.134f,2.9641f}, new float[]{13f,12f,25f,12f,25f,17f,13f,17f}, new float[]{0f,-4.5f,2.2058f}),
        new Quad(new float[]{-5.995f,-5.4641f,5.4641f,-5.995f,-7.4641f,2f,-5.995f,-3.134f,-0.5f,-5.995f,-1.134f,2.9641f}, new float[]{0f,57f,4f,57f,4f,62f,0f,62f}, new float[]{-1f,-5f,1.3397f}),
        new Quad(new float[]{5.995f,-7.4641f,2f,5.995f,-5.4641f,5.4641f,5.995f,-1.134f,2.9641f,5.995f,-3.134f,-0.5f}, new float[]{44f,53f,48f,53f,48f,58f,44f,58f}, new float[]{1f,-5f,1.3397f}),
        new Quad(new float[]{-5.995f,-5.4641f,5.4641f,5.995f,-5.4641f,5.4641f,5.995f,-7.4641f,2f,-5.995f,-7.4641f,2f}, new float[]{12f,42f,0f,42f,0f,46f,12f,46f}, new float[]{0f,-5.866f,1.8397f}),
        new Quad(new float[]{-5.995f,-3.134f,-0.5f,5.995f,-3.134f,-0.5f,5.995f,-1.134f,2.9641f,-5.995f,-1.134f,2.9641f}, new float[]{51f,42f,39f,42f,39f,38f,51f,38f}, new float[]{0f,-4.134f,0.8397f}),
        new Quad(new float[]{-5.99f,-2.9641f,1.134f,5.99f,-2.9641f,1.134f,5.99f,0.5f,3.134f,-5.99f,0.5f,3.134f}, new float[]{13f,42f,25f,42f,25f,46f,13f,46f}, new float[]{0f,7.5f,-0.0622f}),
        new Quad(new float[]{5.99f,-5.4641f,5.4641f,-5.99f,-5.4641f,5.4641f,-5.99f,-2f,7.4641f,5.99f,-2f,7.4641f}, new float[]{26f,42f,38f,42f,38f,46f,26f,46f}, new float[]{0f,6.5f,1.6699f}),
        new Quad(new float[]{-5.99f,-5.4641f,5.4641f,-5.99f,-2.9641f,1.134f,-5.99f,0.5f,3.134f,-5.99f,-2f,7.4641f}, new float[]{11f,57f,16f,57f,16f,61f,11f,61f}, new float[]{-1f,7f,0.8038f}),
        new Quad(new float[]{5.99f,-2.9641f,1.134f,5.99f,-5.4641f,5.4641f,5.99f,-2f,7.4641f,5.99f,0.5f,3.134f}, new float[]{5f,57f,10f,57f,10f,61f,5f,61f}, new float[]{1f,7f,0.8038f}),
        new Quad(new float[]{-5.99f,-5.4641f,5.4641f,5.99f,-5.4641f,5.4641f,5.99f,-2.9641f,1.134f,-5.99f,-2.9641f,1.134f}, new float[]{25f,18f,13f,18f,13f,23f,25f,23f}, new float[]{0f,6.134f,0.3038f}),
        new Quad(new float[]{-5.99f,0.5f,3.134f,5.99f,0.5f,3.134f,5.99f,-2f,7.4641f,-5.99f,-2f,7.4641f}, new float[]{12f,23f,0f,23f,0f,18f,12f,18f}, new float[]{0f,7.866f,1.3038f}),
        new Quad(new float[]{-6f,-2f,2.4641f,6f,-2f,2.4641f,6f,2f,2.4641f,-6f,2f,2.4641f}, new float[]{39f,43f,51f,43f,51f,47f,39f,47f}, new float[]{0f,0f,-1f}),
        new Quad(new float[]{6f,-2f,7.4641f,-6f,-2f,7.4641f,-6f,2f,7.4641f,6f,2f,7.4641f}, new float[]{0f,47f,12f,47f,12f,51f,0f,51f}, new float[]{0f,0f,1f}),
        new Quad(new float[]{-6f,-2f,7.4641f,-6f,-2f,2.4641f,-6f,2f,2.4641f,-6f,2f,7.4641f}, new float[]{23f,57f,28f,57f,28f,61f,23f,61f}, new float[]{-1f,0f,0f}),
        new Quad(new float[]{6f,-2f,2.4641f,6f,-2f,7.4641f,6f,2f,7.4641f,6f,2f,2.4641f}, new float[]{17f,57f,22f,57f,22f,61f,17f,61f}, new float[]{1f,0f,0f}),
        new Quad(new float[]{-6f,-2f,7.4641f,6f,-2f,7.4641f,6f,-2f,2.4641f,-6f,-2f,2.4641f}, new float[]{25f,24f,13f,24f,13f,29f,25f,29f}, new float[]{0f,-1f,0f}),
        new Quad(new float[]{-6f,2f,2.4641f,6f,2f,2.4641f,6f,2f,7.4641f,-6f,2f,7.4641f}, new float[]{12f,29f,0f,29f,0f,24f,12f,24f}, new float[]{0f,1f,0f}),
        new Quad(new float[]{-5.995f,-0.5f,3.134f,5.995f,-0.5f,3.134f,5.995f,2.9641f,1.134f,-5.995f,2.9641f,1.134f}, new float[]{13f,47f,25f,47f,25f,51f,13f,51f}, new float[]{0f,-6.9641f,5.9378f}),
        new Quad(new float[]{5.995f,2f,7.4641f,-5.995f,2f,7.4641f,-5.995f,5.4641f,5.4641f,5.995f,5.4641f,5.4641f}, new float[]{26f,47f,38f,47f,38f,51f,26f,51f}, new float[]{0f,-5.9641f,7.6699f}),
        new Quad(new float[]{-5.995f,2f,7.4641f,-5.995f,-0.5f,3.134f,-5.995f,2.9641f,1.134f,-5.995f,5.4641f,5.4641f}, new float[]{57f,40f,62f,40f,62f,44f,57f,44f}, new float[]{-1f,-6.4641f,6.8038f}),
        new Quad(new float[]{5.995f,-0.5f,3.134f,5.995f,2f,7.4641f,5.995f,5.4641f,5.4641f,5.995f,2.9641f,1.134f}, new float[]{29f,57f,34f,57f,34f,61f,29f,61f}, new float[]{1f,-6.4641f,6.8038f}),
        new Quad(new float[]{-5.995f,2f,7.4641f,5.995f,2f,7.4641f,5.995f,-0.5f,3.134f,-5.995f,-0.5f,3.134f}, new float[]{38f,6f,26f,6f,26f,11f,38f,11f}, new float[]{0f,-7.3301f,7.3038f}),
        new Quad(new float[]{-5.995f,2.9641f,1.134f,5.995f,2.9641f,1.134f,5.995f,5.4641f,5.4641f,-5.995f,5.4641f,5.4641f}, new float[]{38f,5f,26f,5f,26f,0f,38f,0f}, new float[]{0f,-5.5981f,6.3038f}),
        new Quad(new float[]{-5.99f,3.134f,-0.5f,5.99f,3.134f,-0.5f,5.99f,7.4641f,2f,-5.99f,7.4641f,2f}, new float[]{0f,0f,12f,0f,12f,5f,0f,5f}, new float[]{0f,8.9641f,-5.5263f}),
        new Quad(new float[]{5.99f,1.134f,2.9641f,-5.99f,1.134f,2.9641f,-5.99f,5.4641f,5.4641f,5.99f,5.4641f,5.4641f}, new float[]{0f,6f,12f,6f,12f,11f,0f,11f}, new float[]{0f,7.9641f,-3.7942f}),
        new Quad(new float[]{-5.99f,1.134f,2.9641f,-5.99f,3.134f,-0.5f,-5.99f,7.4641f,2f,-5.99f,5.4641f,5.4641f}, new float[]{52f,46f,56f,46f,56f,51f,52f,51f}, new float[]{-1f,8.4641f,-4.6603f}),
        new Quad(new float[]{5.99f,3.134f,-0.5f,5.99f,1.134f,2.9641f,5.99f,5.4641f,5.4641f,5.99f,7.4641f,2f}, new float[]{52f,40f,56f,40f,56f,45f,52f,45f}, new float[]{1f,8.4641f,-4.6603f}),
        new Quad(new float[]{-5.99f,1.134f,2.9641f,5.99f,1.134f,2.9641f,5.99f,3.134f,-0.5f,-5.99f,3.134f,-0.5f}, new float[]{51f,23f,39f,23f,39f,27f,51f,27f}, new float[]{0f,7.5981f,-5.1603f}),
        new Quad(new float[]{-5.99f,7.4641f,2f,5.99f,7.4641f,2f,5.99f,5.4641f,5.4641f,-5.99f,5.4641f,5.4641f}, new float[]{51f,22f,39f,22f,39f,18f,51f,18f}, new float[]{0f,9.3301f,-4.1603f}),
        new Quad(new float[]{-6f,2.4641f,-2f,6f,2.4641f,-2f,6f,7.4641f,-2f,-6f,7.4641f,-2f}, new float[]{26f,12f,38f,12f,38f,17f,26f,17f}, new float[]{0f,0f,-1f}),
        new Quad(new float[]{6f,2.4641f,2f,-6f,2.4641f,2f,-6f,7.4641f,2f,6f,7.4641f,2f}, new float[]{26f,18f,38f,18f,38f,23f,26f,23f}, new float[]{0f,0f,1f}),
        new Quad(new float[]{-6f,2.4641f,2f,-6f,2.4641f,-2f,-6f,7.4641f,-2f,-6f,7.4641f,2f}, new float[]{57f,51f,61f,51f,61f,56f,57f,56f}, new float[]{-1f,0f,0f}),
        new Quad(new float[]{6f,2.4641f,-2f,6f,2.4641f,2f,6f,7.4641f,2f,6f,7.4641f,-2f}, new float[]{57f,45f,61f,45f,61f,50f,57f,50f}, new float[]{1f,0f,0f}),
        new Quad(new float[]{-6f,2.4641f,2f,6f,2.4641f,2f,6f,2.4641f,-2f,-6f,2.4641f,-2f}, new float[]{12f,52f,0f,52f,0f,56f,12f,56f}, new float[]{0f,-1f,0f}),
        new Quad(new float[]{-6f,7.4641f,-2f,6f,7.4641f,-2f,6f,7.4641f,2f,-6f,7.4641f,2f}, new float[]{51f,52f,39f,52f,39f,48f,51f,48f}, new float[]{0f,1f,0f}),
        new Quad(new float[]{-5.995f,1.134f,-2.9641f,5.995f,1.134f,-2.9641f,5.995f,5.4641f,-5.4641f,-5.995f,5.4641f,-5.4641f}, new float[]{26f,24f,38f,24f,38f,29f,26f,29f}, new float[]{0f,-1.5f,7.4019f}),
        new Quad(new float[]{5.995f,3.134f,0.5f,-5.995f,3.134f,0.5f,-5.995f,7.4641f,-2f,5.995f,7.4641f,-2f}, new float[]{0f,30f,12f,30f,12f,35f,0f,35f}, new float[]{0f,-0.5f,9.134f}),
        new Quad(new float[]{-5.995f,3.134f,0.5f,-5.995f,1.134f,-2.9641f,-5.995f,5.4641f,-5.4641f,-5.995f,7.4641f,-2f}, new float[]{49f,58f,53f,58f,53f,63f,49f,63f}, new float[]{-1f,-1f,8.2679f}),
        new Quad(new float[]{5.995f,1.134f,-2.9641f,5.995f,3.134f,0.5f,5.995f,7.4641f,-2f,5.995f,5.4641f,-5.4641f}, new float[]{57f,57f,61f,57f,61f,62f,57f,62f}, new float[]{1f,-1f,8.2679f}),
        new Quad(new float[]{-5.995f,3.134f,0.5f,5.995f,3.134f,0.5f,5.995f,1.134f,-2.9641f,-5.995f,1.134f,-2.9641f}, new float[]{64f,5f,52f,5f,52f,9f,64f,9f}, new float[]{0f,-1.866f,8.7679f}),
        new Quad(new float[]{-5.995f,5.4641f,-5.4641f,5.995f,5.4641f,-5.4641f,5.995f,7.4641f,-2f,-5.995f,7.4641f,-2f}, new float[]{64f,4f,52f,4f,52f,0f,64f,0f}, new float[]{0f,-0.134f,7.7679f}),
        new Quad(new float[]{-5.99f,2f,-7.4641f,5.99f,2f,-7.4641f,5.99f,5.4641f,-5.4641f,-5.99f,5.4641f,-5.4641f}, new float[]{52f,10f,64f,10f,64f,14f,52f,14f}, new float[]{0f,3.5f,-6.9904f}),
        new Quad(new float[]{5.99f,-0.5f,-3.134f,-5.99f,-0.5f,-3.134f,-5.99f,2.9641f,-1.134f,5.99f,2.9641f,-1.134f}, new float[]{13f,52f,25f,52f,25f,56f,13f,56f}, new float[]{0f,2.5f,-5.2583f}),
        new Quad(new float[]{-5.99f,-0.5f,-3.134f,-5.99f,2f,-7.4641f,-5.99f,5.4641f,-5.4641f,-5.99f,2.9641f,-1.134f}, new float[]{41f,59f,46f,59f,46f,63f,41f,63f}, new float[]{-1f,3f,-6.1244f}),
        new Quad(new float[]{5.99f,2f,-7.4641f,5.99f,-0.5f,-3.134f,5.99f,2.9641f,-1.134f,5.99f,5.4641f,-5.4641f}, new float[]{35f,59f,40f,59f,40f,63f,35f,63f}, new float[]{1f,3f,-6.1244f}),
        new Quad(new float[]{-5.99f,-0.5f,-3.134f,5.99f,-0.5f,-3.134f,5.99f,2f,-7.4641f,-5.99f,2f,-7.4641f}, new float[]{38f,30f,26f,30f,26f,35f,38f,35f}, new float[]{0f,2.134f,-6.6244f}),
        new Quad(new float[]{-5.99f,5.4641f,-5.4641f,5.99f,5.4641f,-5.4641f,5.99f,2.9641f,-1.134f,-5.99f,2.9641f,-1.134f}, new float[]{25f,35f,13f,35f,13f,30f,25f,30f}, new float[]{0f,3.866f,-5.6244f}),
        new Quad(new float[]{-6f,-2f,-7.4641f,6f,-2f,-7.4641f,6f,2f,-7.4641f,-6f,2f,-7.4641f}, new float[]{52f,15f,64f,15f,64f,19f,52f,19f}, new float[]{0f,0f,-1f}),
        new Quad(new float[]{6f,-2f,-2.4641f,-6f,-2f,-2.4641f,-6f,2f,-2.4641f,6f,2f,-2.4641f}, new float[]{52f,20f,64f,20f,64f,24f,52f,24f}, new float[]{0f,0f,1f}),
        new Quad(new float[]{-6f,-2f,-2.4641f,-6f,-2f,-7.4641f,-6f,2f,-7.4641f,-6f,2f,-2.4641f}, new float[]{11f,62f,16f,62f,16f,66f,11f,66f}, new float[]{-1f,0f,0f}),
        new Quad(new float[]{6f,-2f,-7.4641f,6f,-2f,-2.4641f,6f,2f,-2.4641f,6f,2f,-7.4641f}, new float[]{5f,62f,10f,62f,10f,66f,5f,66f}, new float[]{1f,0f,0f}),
        new Quad(new float[]{-6f,-2f,-2.4641f,6f,-2f,-2.4641f,6f,-2f,-7.4641f,-6f,-2f,-7.4641f}, new float[]{25f,36f,13f,36f,13f,41f,25f,41f}, new float[]{0f,-1f,0f}),
        new Quad(new float[]{-6f,2f,-7.4641f,6f,2f,-7.4641f,6f,2f,-2.4641f,-6f,2f,-2.4641f}, new float[]{12f,41f,0f,41f,0f,36f,12f,36f}, new float[]{0f,1f,0f}),
        new Quad(new float[]{-5.995f,-5.4641f,-5.4641f,5.995f,-5.4641f,-5.4641f,5.995f,-2f,-7.4641f,-5.995f,-2f,-7.4641f}, new float[]{52f,25f,64f,25f,64f,29f,52f,29f}, new float[]{0f,-0.0359f,1.9378f}),
        new Quad(new float[]{5.995f,-2.9641f,-1.134f,-5.995f,-2.9641f,-1.134f,-5.995f,0.5f,-3.134f,5.995f,0.5f,-3.134f}, new float[]{26f,52f,38f,52f,38f,56f,26f,56f}, new float[]{0f,0.9641f,3.6699f}),
        new Quad(new float[]{-5.995f,-2.9641f,-1.134f,-5.995f,-5.4641f,-5.4641f,-5.995f,-2f,-7.4641f,-5.995f,0.5f,-3.134f}, new float[]{23f,62f,28f,62f,28f,66f,23f,66f}, new float[]{-1f,0.4641f,2.8038f}),
        new Quad(new float[]{5.995f,-5.4641f,-5.4641f,5.995f,-2.9641f,-1.134f,5.995f,0.5f,-3.134f,5.995f,-2f,-7.4641f}, new float[]{17f,62f,22f,62f,22f,66f,17f,66f}, new float[]{1f,0.4641f,2.8038f}),
        new Quad(new float[]{-5.995f,-2.9641f,-1.134f,5.995f,-2.9641f,-1.134f,5.995f,-5.4641f,-5.4641f,-5.995f,-5.4641f,-5.4641f}, new float[]{51f,0f,39f,0f,39f,5f,51f,5f}, new float[]{0f,-0.4019f,3.3038f}),
        new Quad(new float[]{-5.995f,-2f,-7.4641f,5.995f,-2f,-7.4641f,5.995f,0.5f,-3.134f,-5.995f,0.5f,-3.134f}, new float[]{38f,41f,26f,41f,26f,36f,38f,36f}, new float[]{0f,1.3301f,2.3038f}),
        new Quad(new float[]{-5.99f,-5.4641f,-5.4641f,5.99f,-5.4641f,-5.4641f,5.99f,-1.134f,-2.9641f,-5.99f,-1.134f,-2.9641f}, new float[]{39f,6f,51f,6f,51f,11f,39f,11f}, new float[]{0f,2.0359f,-1.5263f}),
        new Quad(new float[]{5.99f,-7.4641f,-2f,-5.99f,-7.4641f,-2f,-5.99f,-3.134f,0.5f,5.99f,-3.134f,0.5f}, new float[]{39f,12f,51f,12f,51f,17f,39f,17f}, new float[]{0f,1.0359f,0.2058f}),
        new Quad(new float[]{-5.99f,-7.4641f,-2f,-5.99f,-5.4641f,-5.4641f,-5.99f,-1.134f,-2.9641f,-5.99f,-3.134f,0.5f}, new float[]{62f,45f,66f,45f,66f,50f,62f,50f}, new float[]{-1f,1.5359f,-0.6603f}),
        new Quad(new float[]{5.99f,-5.4641f,-5.4641f,5.99f,-7.4641f,-2f,5.99f,-3.134f,0.5f,5.99f,-1.134f,-2.9641f}, new float[]{29f,62f,33f,62f,33f,67f,29f,67f}, new float[]{1f,1.5359f,-0.6603f}),
        new Quad(new float[]{-5.99f,-7.4641f,-2f,5.99f,-7.4641f,-2f,5.99f,-5.4641f,-5.4641f,-5.99f,-5.4641f,-5.4641f}, new float[]{64f,35f,52f,35f,52f,39f,64f,39f}, new float[]{0f,0.6699f,-1.1603f}),
        new Quad(new float[]{-5.99f,-1.134f,-2.9641f,5.99f,-1.134f,-2.9641f,5.99f,-3.134f,0.5f,-5.99f,-3.134f,0.5f}, new float[]{64f,34f,52f,34f,52f,30f,64f,30f}, new float[]{0f,2.4019f,-0.1603f}),
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
        c.addVertex(pose, q.pos[i * 3] / 16.0F, q.pos[i * 3 + 1] / 16.0F, q.pos[i * 3 + 2] / 16.0F);
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
