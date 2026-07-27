package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.agmas.noellesroles.content.entity.YouluAnchorEntity;
import org.agmas.noellesroles.init.ModItems;

/**
 * 幽露锚点渲染器：以末影之眼 2D 物品贴图渲染（始终面向摄像机，参考 DanmukuRenderer），
 * 仅对拥有者（幽露本人）可见。
 */
@Environment(EnvType.CLIENT)
public class YouluAnchorRenderer extends EntityRenderer<YouluAnchorEntity> {

    private final ItemRenderer itemRenderer;

    public YouluAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(YouluAnchorEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !entity.isVisibleTo(client.player)) {
            return;
        }

        poseStack.pushPose();

        // ---- 平滑移动（Y轴浮动） ----
        float floatOffset = (float) Math.sin((entity.tickCount + partialTick) * 0.12) * 0.15f; // 幅度0.15，速度可调
        poseStack.translate(0, 0.35 + floatOffset, 0);

        // 始终面向摄像机
        poseStack.mulPose(client.getEntityRenderDispatcher().cameraOrientation());

        // ---- 缩小50% + 呼吸脉动 ----
        float pulse = (float) Math.sin((entity.tickCount + partialTick) * 0.15) * 0.08f + 1.0f;
        float baseScale = 0.5f;                 // 基础缩小50%
        float finalScale = baseScale * pulse;   // 叠加呼吸效果
        poseStack.scale(finalScale, finalScale, finalScale);

        // 渲染物品
        this.itemRenderer.renderStatic(
                ModItems.YOULU_ANCHOR.getDefaultInstance(),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                entity.getId());

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(YouluAnchorEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
