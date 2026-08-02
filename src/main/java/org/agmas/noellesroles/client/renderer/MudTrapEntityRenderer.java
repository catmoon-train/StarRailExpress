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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.content.entity.MudTrapEntity;
import org.joml.Matrix4f;

/**
 * 泥沼陷阱实体渲染器。
 *
 * <p>仅对设陷者（所有者）显示：地面上一圈半透明的泥褐色印记，缓慢旋转、脉动。
 * 对其他玩家完全隐形。
 */
public class MudTrapEntityRenderer extends EntityRenderer<MudTrapEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/misc/enchanted_glint_entity.png");

    public MudTrapEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(MudTrapEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // 只对设陷者（所有者）可见
        if (!entity.isVisibleTo(client.player)) {
            return;
        }

        matrices.pushPose();
        // 平躺在地面上并缓慢旋转
        matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
        float rotation = (entity.tickCount + tickDelta) * 1.5f;
        matrices.mulPose(Axis.ZP.rotationDegrees(rotation));

        float pulse = (float) Math.sin((entity.tickCount + tickDelta) * 0.1) * 0.1f + 1.0f;
        matrices.scale(0.9f * pulse, 0.9f * pulse, 0.9f);

        renderMark(matrices, vertexConsumers, light);
        matrices.popPose();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 泥褐色的方形印记（双面）。 */
    private void renderMark(PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        PoseStack.Pose entry = matrices.last();
        Matrix4f pose = entry.pose();
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        int r = 110, g = 75, b = 40, a = 160;
        float size = 0.5f;

        vertex(consumer, pose, entry, -size, -size, 0, 0, r, g, b, a, light, 1);
        vertex(consumer, pose, entry, -size, size, 0, 1, r, g, b, a, light, 1);
        vertex(consumer, pose, entry, size, size, 1, 1, r, g, b, a, light, 1);
        vertex(consumer, pose, entry, size, -size, 1, 0, r, g, b, a, light, 1);

        vertex(consumer, pose, entry, size, -size, 1, 0, r, g, b, a, light, -1);
        vertex(consumer, pose, entry, size, size, 1, 1, r, g, b, a, light, -1);
        vertex(consumer, pose, entry, -size, size, 0, 1, r, g, b, a, light, -1);
        vertex(consumer, pose, entry, -size, -size, 0, 0, r, g, b, a, light, -1);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, PoseStack.Pose entry,
            float x, float y, float u, float v, int r, int g, int b, int a, int light, int nz) {
        consumer.addVertex(pose, x, y, 0)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 0, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(MudTrapEntity entity) {
        return TEXTURE;
    }
}
