package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.content.entity.CommandBlockCrackEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** 命令方块裂缝：竖着的紫黑色裂口，不使用方块展示。 */
public class CommandBlockCrackRenderer extends EntityRenderer<CommandBlockCrackEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/misc/enchanted_glint_entity.png");

    public CommandBlockCrackRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /**
     * 裂缝带自定义名（仅作识别用）。原版 EntityRenderer 在 customNameVisible 为 false 时，
     * 只要准星指着该实体仍会画名牌（crosshairPickEntity 分支），而裂缝正是要靠指着来打，
     * 所以在渲染器侧直接按掉名牌渲染，保证名字绝不悬浮在裂缝上方。
     */
    @Override
    protected boolean shouldShowName(CommandBlockCrackEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(CommandBlockCrackEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(CommandBlockCrackEntity entity, float yaw, float partialTick, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.18F);
        int seed = entity.getId() * 17;
        matrices.pushPose();
        matrices.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        matrices.translate(0.0F, 0.15F, 0.0F);
        PoseStack.Pose pose = matrices.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        if (entity.isHighlighted()) {
            // 刚刷出来的十秒高亮：外扩一圈白光，让全场都能看到裂缝出现的位置。
            drawJagged(consumer, pose, seed - 7, 1.9F, 0.34F, 255, 245, 255, (int) (110 * pulse), 0x00F000F0);
            drawJagged(consumer, pose, seed - 7, 2.15F, 0.18F, 220, 190, 255, (int) (70 * pulse), 0x00F000F0);
        }
        drawJagged(consumer, pose, seed, 1.55F, 0.16F, 28, 8, 48, (int) (170 * pulse), light);
        drawJagged(consumer, pose, seed + 3, 1.35F, 0.05F, 180, 70, 255, (int) (220 * pulse), 0x00F000F0);
        matrices.popPose();
        super.render(entity, yaw, partialTick, matrices, buffers, light);
    }

    private static void drawJagged(VertexConsumer consumer, PoseStack.Pose pose, int seed, float height, float half,
            int red, int green, int blue, int alpha, int light) {
        int steps = 6;
        float y0 = 0.0F;
        float x0 = jag(seed, 0);
        for (int i = 0; i < steps; i++) {
            float y1 = height * (i + 1) / steps;
            float x1 = jag(seed, i + 1);
            quad(consumer, pose, x0 - half, y0, 0.0F, x0 + half, y0, 0.0F, x1 + half, y1, 0.0F, x1 - half, y1, 0.0F,
                    red, green, blue, alpha, light);
            quad(consumer, pose, x0, y0, -half, x0, y0, half, x1, y1, half, x1, y1, -half, red, green, blue, alpha,
                    light);
            y0 = y1;
            x0 = x1;
        }
    }

    private static float jag(int seed, int step) {
        return Mth.sin((seed + step * 19) * 0.37F) * 0.18F;
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x1, float y1, float z1, float x2,
            float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b,
            int a, int light) {
        Matrix4f matrix = pose.pose();
        vertex(consumer, matrix, pose, x1, y1, z1, 0.0F, 0.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x2, y2, z2, 1.0F, 0.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x3, y3, z3, 1.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x4, y4, z4, 0.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x4, y4, z4, 0.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x3, y3, z3, 1.0F, 1.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x2, y2, z2, 1.0F, 0.0F, r, g, b, a, light);
        vertex(consumer, matrix, pose, x1, y1, z1, 0.0F, 0.0F, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, float x, float y,
            float z, float u, float v, int r, int g, int b, int a, int light) {
        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}