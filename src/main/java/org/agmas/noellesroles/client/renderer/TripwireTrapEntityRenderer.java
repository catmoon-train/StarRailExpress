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
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.joml.Matrix4f;

/**
 * 绊线陷阱实体渲染器（重做版）。
 *
 * <p>所有玩家只能看到墙面锚点处的「出发点」小标记；从锚点沿延伸方向绷紧的橙色
 * 激光线（水平+竖直两片薄面组成十字截面，带轻微脉动）<b>只有杀手阵营与偏狼中立
 * 阵营可见</b>——平民视角下绊线本体是隐形的。
 */
public class TripwireTrapEntityRenderer extends EntityRenderer<TripwireTrapEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/misc/enchanted_glint_entity.png");

    public TripwireTrapEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public boolean shouldRender(TripwireTrapEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
            double x, double y, double z) {
        // 包围盒即绊线本体，直接用它做视锥剔除
        AABB box = entity.getBoundingBox().inflate(0.5);
        return frustum.isVisible(box);
    }

    @Override
    public void render(TripwireTrapEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {

        double length = entity.getWireLength();
        if (length <= 0) {
            return;
        }
        Direction dir = entity.getWireDirection();
        Vec3 delta = new Vec3(dir.getStepX() * length, 0, dir.getStepZ() * length);

        // 轻微脉动的橙色
        float pulse = (float) Math.sin((entity.tickCount + tickDelta) * 0.15) * 0.15f + 0.85f;
        int red = 255;
        int green = (int) (140 * pulse);
        int blue = 0;
        int alpha = 200;

        matrices.pushPose();
        PoseStack.Pose entry = matrices.last();
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        // 出发点标记：贴在墙面锚点的小方片，对所有人可见
        float s = 0.12f;
        float hx = -dir.getStepZ() * s;
        float hz = dir.getStepX() * s;
        drawQuad(consumer, entry,
                hx, s, hz, hx, -s, hz, -hx, -s, -hz, -hx, s, -hz,
                red, green, blue, 230, light);

        // 激光线本体：只有杀手阵营与偏狼中立阵营可见
        if (canSeeLaser()) {
            float halfW = 0.03f;
            float ex = (float) delta.x;
            float ez = (float) delta.z;
            // 竖直薄片（法线水平）
            drawQuad(consumer, entry,
                    0, -halfW, 0, 0, halfW, 0, ex, halfW, ez, ex, -halfW, ez,
                    red, green, blue, alpha, light);
            // 水平薄片（法线竖直）：垂直于线方向的水平偏移
            float px = -ez / (float) length * halfW;
            float pz = ex / (float) length * halfW;
            drawQuad(consumer, entry,
                    px, 0, pz, -px, 0, -pz, ex - px, 0, ez - pz, ex + px, 0, ez + pz,
                    red, green, blue, alpha, light);
        }

        matrices.popPose();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    /** 本地玩家是否能看到绊线激光：杀手阵营或偏狼中立阵营。 */
    private static boolean canSeeLaser() {
        var client = Minecraft.getInstance();
        var game = SREClient.gameComponent;
        if (client.player == null || game == null) {
            return false;
        }
        SRERole role = game.getRole(client.player);
        return role != null && (role.canUseKiller() || role.isNeutralForKiller());
    }

    /** 双面四边形。 */
    private static void drawQuad(VertexConsumer consumer, PoseStack.Pose entry,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            int r, int g, int b, int a, int light) {
        Matrix4f pose = entry.pose();
        // 正面
        vertex(consumer, pose, entry, x1, y1, z1, 0, 0, r, g, b, a, light);
        vertex(consumer, pose, entry, x2, y2, z2, 0, 1, r, g, b, a, light);
        vertex(consumer, pose, entry, x3, y3, z3, 1, 1, r, g, b, a, light);
        vertex(consumer, pose, entry, x4, y4, z4, 1, 0, r, g, b, a, light);
        // 背面
        vertex(consumer, pose, entry, x4, y4, z4, 1, 0, r, g, b, a, light);
        vertex(consumer, pose, entry, x3, y3, z3, 1, 1, r, g, b, a, light);
        vertex(consumer, pose, entry, x2, y2, z2, 0, 1, r, g, b, a, light);
        vertex(consumer, pose, entry, x1, y1, z1, 0, 0, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, PoseStack.Pose entry,
            float x, float y, float z, float u, float v, int r, int g, int b, int a, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(entry, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(TripwireTrapEntity entity) {
        return TEXTURE;
    }
}
