package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.joml.Quaternionf;

/**
 * 60s 海上载具渲染器。本载具继承 {@code Boat}（不是 LivingEntity），故走 {@link EntityRenderer}
 * 而非陆上载具用的 {@code LivingEntityRenderer}；但姿态变换<b>刻意与后者对齐</b>
 * （yaw 旋转 → {@code scale(-1,-1,1)} → {@code translate(0,-1.501,0)}），
 * 这样两套模型能用完全相同的约定书写（基线 y=24），不必为海上单独换一套坐标系。
 * <p>
 * 受击摆动复用原版船的 {@code hurtTime}/{@code hurtDir}，但幅度按<b>本模式的耐久</b>算——
 * 原版的 {@code getDamage()} 我们没在用（{@code hurt} 已改写成扣 vehicleHealth）。
 */
public class SixtySecondsSeaVehicleRenderer extends EntityRenderer<SixtySecondsSeaVehicleEntity> {

    private final SixtySecondsSeaVehicleModel model;
    private final ResourceLocation texture;

    public SixtySecondsSeaVehicleRenderer(EntityRendererProvider.Context context,
            SixtySecondsSeaVehicleEntity.Kind kind) {
        super(context);
        this.model = new SixtySecondsSeaVehicleModel(
                SixtySecondsSeaVehicleModel.createFor(kind).bakeRoot());
        this.texture = Noellesroles.id("textures/entity/" + textureName(kind) + ".png");
        this.shadowRadius = kind == SixtySecondsSeaVehicleEntity.Kind.FISHING_BOAT ? 1.2F : 0.8F;
    }

    private static String textureName(SixtySecondsSeaVehicleEntity.Kind kind) {
        return switch (kind) {
            case RAFT -> "sixty_seconds_raft";
            case MOTORBOAT -> "sixty_seconds_motorboat";
            case FISHING_BOAT -> "sixty_seconds_fishing_boat";
        };
    }

    @Override
    public void render(SixtySecondsSeaVehicleEntity entity, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 受击摆动：摆幅随「已损耐久占比」放大，血越少晃得越狠
        float hurt = (float) entity.getHurtTime() - partialTicks;
        if (hurt > 0.0F) {
            float lost = 1.0F - (float) entity.vehicleHealth() / Math.max(1, entity.maxVehicleHealth());
            poseStack.mulPose(Axis.XP.rotationDegrees(
                    Mth.sin(hurt) * hurt * (0.5F + lost * 2.0F) / 10.0F * entity.getHurtDir()));
        }
        // 出水回正（原版船离开水面时的翻正动画）
        float bubble = entity.getBubbleAngle(partialTicks);
        if (!Mth.equal(bubble, 0.0F)) {
            poseStack.mulPose(new Quaternionf().setAngleAxis(
                    bubble * (float) (Math.PI / 180.0), 1.0F, 0.0F, 0.0F));
        }

        // 与 LivingEntityRenderer 等价的收尾：翻到模型空间 + 抬到基线 y=24 对应的高度
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        model.setupAnim(entity, partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(model.renderType(texture));
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsSeaVehicleEntity entity) {
        return texture;
    }
}
