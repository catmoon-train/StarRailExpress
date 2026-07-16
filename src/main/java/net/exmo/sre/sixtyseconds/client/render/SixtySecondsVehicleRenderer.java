package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 60s 载具渲染器：按 Kind 选模型与贴图，摩托车缩放 2x、汽车缩放 3x。
 */
public class SixtySecondsVehicleRenderer
        extends LivingEntityRenderer<SixtySecondsVehicleEntity, SixtySecondsVehicleModel> {

    private static final ResourceLocation MOTORCYCLE_TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_motorcycle.png");
    private static final ResourceLocation CAR_TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_car.png");

    private final SixtySecondsVehicleEntity.Kind kind;
    private final float modelScale;

    public SixtySecondsVehicleRenderer(EntityRendererProvider.Context context,
            SixtySecondsVehicleEntity.Kind kind) {
        super(context, new SixtySecondsVehicleModel(
                (kind == SixtySecondsVehicleEntity.Kind.CAR
                        ? SixtySecondsVehicleModel.createCar()
                        : SixtySecondsVehicleModel.createMotorcycle()).bakeRoot()),
                kind == SixtySecondsVehicleEntity.Kind.CAR ? 2.7F : 1.0F);
        this.kind = kind;
        this.modelScale = kind == SixtySecondsVehicleEntity.Kind.CAR ? 3.0F : 2.0F;
    }

    @Override
    protected void scale(SixtySecondsVehicleEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsVehicleEntity entity) {
        return kind == SixtySecondsVehicleEntity.Kind.CAR ? CAR_TEXTURE : MOTORCYCLE_TEXTURE;
    }

    @Override
    protected boolean shouldShowName(SixtySecondsVehicleEntity entity) {
        return false;
    }
}
