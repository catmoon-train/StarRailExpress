package org.agmas.noellesroles.client.renderer;

import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 60s NPC 渲染器：复用<b>原版僵尸的模型网格</b>（{@link ModelLayers#ZOMBIE}，标准 64×64 人形 UV），
 * 只按变体换贴图——不注册自有 {@code EntityModelLayer}，不写自有模型类。
 * <p>注意用 {@link HumanoidModel} + {@link HumanoidMobRenderer} 而非 {@code ZombieModel}/{@code ZombieRenderer}：
 * 后者泛型绑死 {@code T extends Zombie}，而 NPC 是 {@code PathfinderMob}。副作用是 NPC 用普通人形手臂
 * 摆动而非僵尸的平举双臂——对活人 NPC 反而更合适。
 */
public class SixtySecondsNpcRenderer extends HumanoidMobRenderer<SixtySecondsNpcEntity,
        HumanoidModel<SixtySecondsNpcEntity>> {

    public SixtySecondsNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SixtySecondsNpcEntity entity) {
        return entity.textureLocation();
    }
}
