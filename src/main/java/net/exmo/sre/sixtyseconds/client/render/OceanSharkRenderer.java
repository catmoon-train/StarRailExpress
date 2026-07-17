package net.exmo.sre.sixtyseconds.client.render;

import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 鲨鱼渲染器：缩放由 {@code Attributes.SCALE} 自动处理（碰撞箱/渲染一并放大），
 * 朝向用生物 yaw（不同于船的 180° 反转），走 {@link MobRenderer} 的默认朝向管线。
 * <p>
 * 贴图按变体切换（{@link OceanSharkEntity#textureLocation()}）。原来这里覆写了 {@code render}，
 * 里头有一处 {@code pushPose()} 没有对应的 {@code popPose()}——会污染 PoseStack、连累后续实体渲染错位，
 * 且那段「水中微调 pitch」实际什么都没做（算了个 swimPitch 却没用）。已整段删除，走父类默认渲染。
 */
public class OceanSharkRenderer extends MobRenderer<OceanSharkEntity, OceanSharkModel> {

    public OceanSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new OceanSharkModel(OceanSharkModel.create().bakeRoot()), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(OceanSharkEntity entity) {
        return entity.textureLocation();
    }
}
