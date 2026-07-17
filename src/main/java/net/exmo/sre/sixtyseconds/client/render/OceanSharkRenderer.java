package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;

/**
 * 鲨鱼渲染器：缩放由 {@code Attributes.SCALE} 自动处理（碰撞箱/渲染一并放大），
 * 朝向用生物 yaw（不同于船的 180° 反转），走 {@link MobRenderer} 的默认朝向管线。
 */
public class OceanSharkRenderer extends MobRenderer<OceanSharkEntity, OceanSharkModel> {

    private static final ResourceLocation DEFAULT_TEXTURE =
            Noellesroles.id("textures/entity/ocean_reef_shark.png");

    public OceanSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new OceanSharkModel(OceanSharkModel.create().bakeRoot()), 0.5F);
    }

    @Override
    public void render(OceanSharkEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 在水中微调 pitch（模拟游泳上下摆动）
        if (entity.isInWater()) {
            float swimPitch = Mth.sin(entity.tickCount * 0.08F + partialTicks * 0.08F) * 3.0F;
            poseStack.pushPose();
            // 应用额外的俯仰
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(OceanSharkEntity entity) {
        return entity.textureLocation();
    }
}
