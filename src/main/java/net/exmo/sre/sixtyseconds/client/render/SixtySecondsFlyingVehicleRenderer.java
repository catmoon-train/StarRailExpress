package net.exmo.sre.sixtyseconds.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsFlyingVehicleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 60s 飞行载具渲染器：按 Kind 选模型与贴图，飞行器 ×2、直升机 ×2.5、飞机 ×3.5 缩放。
 */
public class SixtySecondsFlyingVehicleRenderer
        extends LivingEntityRenderer<SixtySecondsFlyingVehicleEntity, SixtySecondsFlyingVehicleModel> {

    private final ResourceLocation texture;
    private final float modelScale;

    public SixtySecondsFlyingVehicleRenderer(EntityRendererProvider.Context context,
            SixtySecondsFlyingVehicleEntity.Kind kind) {
        super(context, new SixtySecondsFlyingVehicleModel(
                SixtySecondsFlyingVehicleModel.createFor(kind).bakeRoot()), 0.5F);
        this.texture = Noellesroles.id("textures/entity/" + textureName(kind) + ".png");
        this.modelScale = switch (kind) {
            case FLYER -> 2.0F;
            case HELICOPTER -> 2.5F;
            case AIRPLANE -> 3.5F;
        };
    }

    private static String textureName(SixtySecondsFlyingVehicleEntity.Kind kind) {
        return switch (kind) {
            case FLYER -> "sixty_seconds_flyer";
            case HELICOPTER -> "sixty_seconds_helicopter";
            case AIRPLANE -> "sixty_seconds_airplane";
        };
    }

    @Override
    protected void scale(SixtySecondsFlyingVehicleEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(modelScale, modelScale, modelScale);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsFlyingVehicleEntity entity) {
        return texture;
    }

    @Override
    protected boolean shouldShowName(SixtySecondsFlyingVehicleEntity entity) {
        return false;
    }
}
