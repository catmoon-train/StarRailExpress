package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.command_block.FogGuestZoneEntity;

/** 封闭的不透明雾体，包含顶底，雾内不画外壳以保留内部视野。 */
public class FogGuestZoneRenderer extends EntityRenderer<FogGuestZoneEntity> {
    private static final int SEGMENTS = 64;

    public FogGuestZoneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(FogGuestZoneEntity entity, float yaw, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light) {
        if (entity.contains(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poses.last();
        // 外接多边形不在圆周边缘留下细小可见缝隙。
        float radius = (float) (FogGuestZoneEntity.RADIUS / Math.cos(Math.PI / SEGMENTS));
        float height = (float) FogGuestZoneEntity.HEIGHT;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = Math.PI * 2 * i / SEGMENTS;
            double b = Math.PI * 2 * (i + 1) / SEGMENTS;
            float x1 = (float) Math.cos(a) * radius, z1 = (float) Math.sin(a) * radius;
            float x2 = (float) Math.cos(b) * radius, z2 = (float) Math.sin(b) * radius;
            vertex(vertices, pose, x1, 0, z1);
            vertex(vertices, pose, x1, height, z1);
            vertex(vertices, pose, x2, height, z2);
            vertex(vertices, pose, x2, 0, z2);
            // 退化四边形组成顶底扇面，兼容原版 QUADS 批次。
            vertex(vertices, pose, 0, height, 0);
            vertex(vertices, pose, x2, height, z2);
            vertex(vertices, pose, x1, height, z1);
            vertex(vertices, pose, 0, height, 0);
            vertex(vertices, pose, 0, 0, 0);
            vertex(vertices, pose, x1, 0, z1);
            vertex(vertices, pose, x2, 0, z2);
            vertex(vertices, pose, 0, 0, 0);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z) {
        consumer.addVertex(pose, x, y, z).setColor(169, 187, 193, 255);
    }

    @Override
    public ResourceLocation getTextureLocation(FogGuestZoneEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
