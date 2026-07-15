package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.jspecify.annotations.Nullable;

/**
 * 飞斧实体渲染器 —— 复用原版箭矢渲染器（沿飞行方向朝向 + 十字截面），
 * 贴图使用 {@code textures/entity/throwing_axe.png}（绘制在箭矢 UV 布局的枪身区域）。
 *
 * <p>沿用飞刀渲染器的时停守卫：时停期间不可移动的玩家看不到飞行中的飞斧。
 */
public class ThrowingAxeRenderer extends ArrowRenderer {
    public ThrowingAxeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @Nullable ResourceLocation getTextureLocation(Entity entity) {
        return ResourceLocation.tryParse("noellesroles:textures/entity/throwing_axe.png");
    }

    @Override
    public void render(AbstractArrow entity, float f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource,
            int i) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (player.hasEffect(ModEffects.TIME_STOP)) {
                if (!TimeStopEffect.canMovePlayers.contains(player.getUUID()))
                    return;
            }
        }

        super.render(entity, f, g, poseStack, multiBufferSource, i);
    }
}
