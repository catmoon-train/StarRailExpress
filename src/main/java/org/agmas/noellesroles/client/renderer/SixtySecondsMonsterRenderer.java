package org.agmas.noellesroles.client.renderer;

import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 末日60秒自研怪物渲染器：复用原版僵尸模型/动画，贴图按实体变体（拖行者/奔跑者/重锤兽/吐酸者）
 * 或 Boss 专属贴图切换（{@link SixtySecondsMonsterEntity#textureLocation()}）。
 * Boss 体型放大由 {@code Attributes.SCALE} 驱动，渲染层无需处理。
 */
public class SixtySecondsMonsterRenderer extends ZombieRenderer {

    public SixtySecondsMonsterRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        if (entity instanceof SixtySecondsMonsterEntity monster) {
            return monster.textureLocation();
        }
        return super.getTextureLocation(entity);
    }
}
