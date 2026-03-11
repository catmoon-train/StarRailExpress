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
import org.agmas.noellesroles.entity.CalamityMarkEntity;
import org.joml.Matrix4f;

/**
 * 灾厄印记实体渲染器
 * 
 * 对设陷者显示半透明的紫色印记
 * 对其他玩家完全隐形
 */
public class CalamityMarkEntityRenderer extends EntityRenderer<CalamityMarkEntity> {
    
    // 使用附魔光效纹理作为印记效果
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/enchanted_glint_entity.png");
    
    public CalamityMarkEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public void render(CalamityMarkEntity entity, float yaw, float tickDelta, PoseStack matrices, 
                       MultiBufferSource vertexConsumers, int light) {
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        // 只对设陷者（所有者）可见
        if (!entity.isVisibleTo(client.player)) {
            return;
        }
        
        matrices.pushPose();
        
        // 旋转使其平躺在地面上
        matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
        
        // 缓慢旋转动画
        float rotation = (entity.tickCount + tickDelta) * 2.0f;
        matrices.mulPose(Axis.ZP.rotationDegrees(rotation));
        
        // 缩放
        float scale = 0.8f;
        // 添加脉动效果
        float pulse = (float) Math.sin((entity.tickCount + tickDelta) * 0.1) * 0.1f + 1.0f;
        matrices.scale(scale * pulse, scale * pulse, scale);
        
        // 渲染半透明的印记
        renderMark(matrices, vertexConsumers, light);
        
        matrices.popPose();
        
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }
    
    /**
     * 渲染印记图形
     */
    private void renderMark(PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        PoseStack.Pose entry = matrices.last();
        Matrix4f posMatrix = entry.pose();
        
        // 使用半透明渲染层
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        
        // 紫色半透明
        int red = 139;
        int green = 69;
        int blue = 200;
        int alpha = 150;
        
        float size = 0.5f;
        
        // 绘制六边形印记
        drawHexagon(vertexConsumer, posMatrix, entry, size, red, green, blue, alpha, light);
    }
    
    /**
     * 绘制六边形
     */
    private void drawHexagon(VertexConsumer consumer, Matrix4f posMatrix, PoseStack.Pose entry,
                             float size, int r, int g, int b, int a, int light) {
        // 中心点
        float cx = 0, cy = 0;
        
        // 绘制六个三角形组成六边形
        for (int i = 0; i < 6; i++) {
            float angle1 = (float) (Math.PI / 3 * i);
            float angle2 = (float) (Math.PI / 3 * (i + 1));
            
            float x1 = (float) (size * Math.cos(angle1));
            float y1 = (float) (size * Math.sin(angle1));
            float x2 = (float) (size * Math.cos(angle2));
            float y2 = (float) (size * Math.sin(angle2));
            
            // 三角形顶点
            consumer.addVertex(posMatrix, cx, cy, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, 1);
            
            consumer.addVertex(posMatrix, x1, y1, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f + x1 / size * 0.5f, 0.5f + y1 / size * 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, 1);
            
            consumer.addVertex(posMatrix, x2, y2, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f + x2 / size * 0.5f, 0.5f + y2 / size * 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, 1);
            
            // 第二个三角形（背面）
            consumer.addVertex(posMatrix, cx, cy, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, -1);
            
            consumer.addVertex(posMatrix, x2, y2, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f + x2 / size * 0.5f, 0.5f + y2 / size * 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, -1);
            
            consumer.addVertex(posMatrix, x1, y1, 0)
                    .setColor(r, g, b, a)
                    .setUv(0.5f + x1 / size * 0.5f, 0.5f + y1 / size * 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, 0, 0, -1);
        }
    }
    
    @Override
    public ResourceLocation getTextureLocation(CalamityMarkEntity entity) {
        return TEXTURE;
    }
}