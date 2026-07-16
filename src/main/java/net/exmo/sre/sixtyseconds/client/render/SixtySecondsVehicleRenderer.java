package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 60s 载具渲染器：按 Kind 选模型与贴图（模型内联 bake，不注册 ModelLayer）。
 */
public class SixtySecondsVehicleRenderer
        extends LivingEntityRenderer<SixtySecondsVehicleEntity, SixtySecondsVehicleModel> {

    private static final ResourceLocation MOTORCYCLE_TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_motorcycle.png");
    private static final ResourceLocation CAR_TEXTURE =
            Noellesroles.id("textures/entity/sixty_seconds_car.png");

    private final SixtySecondsVehicleEntity.Kind kind;

    public SixtySecondsVehicleRenderer(EntityRendererProvider.Context context,
            SixtySecondsVehicleEntity.Kind kind) {
        super(context, new SixtySecondsVehicleModel(
                (kind == SixtySecondsVehicleEntity.Kind.CAR
                        ? SixtySecondsVehicleModel.createCar()
                        : SixtySecondsVehicleModel.createMotorcycle()).bakeRoot()),
                kind == SixtySecondsVehicleEntity.Kind.CAR ? 0.9F : 0.5F);
        this.kind = kind;
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
