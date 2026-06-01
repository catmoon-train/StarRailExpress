package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.EmojiHelmetItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class EmojiHelmetRenderer {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[] {
            SRE.id("textures/item/emoji/emoji_0.png"),
            SRE.id("textures/item/emoji/emoji_1.png"),
            SRE.id("textures/item/emoji/emoji_2.png"),
            SRE.id("textures/item/emoji/emoji_3.png"),
            SRE.id("textures/item/emoji/emoji_4.png")
    };

    private EmojiHelmetRenderer() {
    }

    public static void renderOnFace(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        int index = EmojiHelmetItem.getEmojiIndex(stack);
        ResourceLocation texture = TEXTURES[Math.floorMod(index, TEXTURES.length)];

        poseStack.pushPose();
        poseStack.translate(0.0F, -0.25F, -0.255F);

        float size = 0.32F;
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));

        consumer.addVertex(matrix, -size, -size, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, -size, size, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, size, size, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, size, -size, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);

        poseStack.popPose();
    }
}
