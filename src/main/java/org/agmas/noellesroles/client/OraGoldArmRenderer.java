/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.role_data.vigilante.JojoRoleData;

/**
 * 欧拉一拳持有时的金色手臂包裹：沿时间做金-橙渐变，略带发光。
 */
public final class OraGoldArmRenderer {
    private OraGoldArmRenderer() {
    }

    public static boolean shouldWrap(AbstractClientPlayer player) {
        return JojoRoleData.isHoldingOraPunch(player);
    }

    public static int goldColor(float ageInTicks) {
        float pulse = (Mth.sin(ageInTicks * 0.22f) + 1.0f) * 0.5f;
        int r = 255;
        int g = (int) Mth.lerp(pulse, 168, 230);
        int b = (int) Mth.lerp(pulse, 24, 90);
        int a = (int) Mth.lerp(pulse, 150, 220);
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static int goldColorOuter(float ageInTicks) {
        float pulse = (Mth.sin(ageInTicks * 0.22f + 1.2f) + 1.0f) * 0.5f;
        int r = 255;
        int g = (int) Mth.lerp(pulse, 200, 248);
        int b = (int) Mth.lerp(pulse, 80, 160);
        int a = (int) Mth.lerp(pulse, 80, 140);
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    public static void renderArmPair(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer player,
            PoseStack poseStack, MultiBufferSource buffer, int light, float ageInTicks) {
        ResourceLocation skin = player.getSkin().texture();
        VertexConsumer inner = buffer.getBuffer(RenderType.entityTranslucent(skin));
        int innerColor = goldColor(ageInTicks);
        renderPart(model.rightArm, poseStack, inner, light, innerColor);
        renderPart(model.leftArm, poseStack, inner, light, innerColor);

        VertexConsumer outer = buffer.getBuffer(RenderType.entityTranslucent(skin));
        int outerColor = goldColorOuter(ageInTicks);
        if (model.rightSleeve.visible) {
            renderPart(model.rightSleeve, poseStack, outer, light, outerColor);
        }
        if (model.leftSleeve.visible) {
            renderPart(model.leftSleeve, poseStack, outer, light, outerColor);
        }
    }

    public static void renderSingleArm(ModelPart arm, ModelPart sleeve, AbstractClientPlayer player,
            PoseStack poseStack, MultiBufferSource buffer, int light, float ageInTicks) {
        ResourceLocation skin = player.getSkin().texture();
        VertexConsumer inner = buffer.getBuffer(RenderType.entityTranslucent(skin));
        renderPart(arm, poseStack, inner, light, goldColor(ageInTicks));
        if (sleeve != null) {
            VertexConsumer outer = buffer.getBuffer(RenderType.entityTranslucent(skin));
            renderPart(sleeve, poseStack, outer, light, goldColorOuter(ageInTicks));
        }
    }

    private static void renderPart(ModelPart part, PoseStack poseStack, VertexConsumer consumer, int light, int color) {
        if (part == null || !part.visible) {
            return;
        }
        part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
    }
}
