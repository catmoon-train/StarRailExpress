package cn.zbx1425.minopp.render;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.block.BlockMinoTable;
import cn.zbx1425.minopp.game.Card;
import cn.zbx1425.minopp.platform.RegistryObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Random;

public class BlockEntityMinoTableRenderer implements BlockEntityRenderer<BlockEntityMinoTable> {

    private static final RegistryObject<ItemStack> HAND_CARDS_MODEL_PLACEHOLDER = new RegistryObject<>(() -> new ItemStack(Mino.ITEM_HAND_CARDS_MODEL_PLACEHOLDER.get()));
    private static final RegistryObject<ItemStack> HAND_CARDS_ENCHANTED_MODEL_PLACEHOLDER = new RegistryObject<>(() -> {
        ItemStack stack = new ItemStack(Mino.ITEM_HAND_CARDS_MODEL_PLACEHOLDER.get());
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    });


    private ItemRenderer itemRenderer;

    public BlockEntityMinoTableRenderer(BlockEntityRendererProvider.Context ctx) {
        itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(BlockEntityMinoTable blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.game == null) return;

        if (BlockMinoTable.Client.isCursorHittingPile()) {
            LevelRenderer.renderLineBox(poseStack, multiBufferSource.getBuffer(RenderType.lines()),
                    BlockMinoTable.Client.getPileAabb(blockEntity), 1, 1, 0, 1f);
        }

        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.entityCutout(Mino.id("textures/gui/deck.png")));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.94, 0.5);
        poseStack.scale(0.4f, 0.3f, 0.4f);
        BakedModel model = itemRenderer.getModel(HAND_CARDS_MODEL_PLACEHOLDER.get(), null, null, 0);
        poseStack.mulPose(Axis.XP.rotation(-(float)Math.PI / 2));
        Random deckRandom = new Random(1);
        for (int ci = 0; ci < Math.ceil(blockEntity.game.deck.size() / 5f); ci++) {
            poseStack.translate(deckRandom.nextFloat() * 0.1 - 0.05, deckRandom.nextFloat() * 0.1 - 0.05, 1 / 16f);
            itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), ItemDisplayContext.FIXED, false,
                    poseStack, multiBufferSource, packedLight, packedOverlay, model);
        }
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(1, 0.9 + 1 / 16f, 1);
        poseStack.scale(0.2f, 0.2f, 0.2f);
        poseStack.mulPose(Axis.XP.rotation(-(float)Math.PI / 2));
        Random discardRandom = new Random(1);
        for (int ci = 0; ci <= blockEntity.game.discardDeck.size(); ci++) {
            poseStack.pushPose();
            poseStack.translate(discardRandom.nextFloat() * 6 - 3, discardRandom.nextFloat() * 6 - 3, ci / 32f);
            poseStack.mulPose(Axis.ZP.rotation(discardRandom.nextFloat() * 2 * (float)Math.PI));

            Card card = ci == blockEntity.game.discardDeck.size() ? blockEntity.game.topCard : blockEntity.game.discardDeck.get(ci);
            float cardU = switch (card.family) {
                case NUMBER -> Math.abs(card.number) * 16;
                case SKIP -> 160;
                case DRAW -> 176;
                case REVERSE -> 192;
            } / 256f;
            float cardV = card.suit.ordinal() * 25 / 128f;
            float cardUW = 16 / 256f;
            float cardVH = 25 / 128f;
            int color = (ci == blockEntity.game.discardDeck.size())
                    ? 0xFFFFFFFF : 0xFFBBBBBB;
            vertexConsumer
                    .addVertex(poseStack.last(), -0.52f, 0.8f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU, cardV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFF000000)
                    .addVertex(poseStack.last(), -0.52f, -0.8f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU, cardV + cardVH).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFF000000)
                    .addVertex(poseStack.last(), 0.52f, -0.8f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU + cardUW, cardV + cardVH).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFF000000)
                    .addVertex(poseStack.last(), 0.52f, 0.8f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU + cardUW, cardV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFF000000);
            poseStack.translate(0, 0, 1 / 64f);
            vertexConsumer
                    .addVertex(poseStack.last(), -0.5f, 0.78f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU, cardV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(color)
                    .addVertex(poseStack.last(), -0.5f, -0.78f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU, cardV + cardVH).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(color)
                    .addVertex(poseStack.last(), 0.5f, -0.78f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU + cardUW, cardV + cardVH).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(color)
                    .addVertex(poseStack.last(), 0.5f, 0.78f, 0).setNormal(poseStack.last(), 0, 0, 1)
                    .setUv(cardU + cardUW, cardV).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(color);

            if (ci == blockEntity.game.discardDeck.size()) {
//                itemRenderer.render(HAND_CARDS_ENCHANTED_MODEL_PLACEHOLDER.get(), ItemDisplayContext.FIXED, false,
//                        poseStack, multiBufferSource, i, j, model);
                Font font = Minecraft.getInstance().font;
                poseStack.mulPose(Axis.XP.rotation((float)Math.PI / 2));
                poseStack.translate(0, 1f, 0);

//                poseStack.translate(0, 0, 1f);
//                poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                poseStack.scale(0.03F, -0.03F, 0.03F);
                Matrix4f matrix4f = poseStack.last().pose();
                float g = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                int k = (int)(g * 255.0F) << 24;
                Component component = (card.suit == Card.Suit.WILD)
                    ? card.getDisplayName().copy().append(Component.translatable("game.minopp.card.suit." + card.getEquivSuit().name().toLowerCase()))
                    : card.getDisplayName();
                float h = (float)(-font.width(component) / 2);
                font.drawInBatch(component, h, 0, 553648127, false, matrix4f, multiBufferSource, Font.DisplayMode.SEE_THROUGH, k, LightTexture.FULL_BRIGHT);
                font.drawInBatch(component, h, 0, -1, false, matrix4f, multiBufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                poseStack.mulPose(Axis.YP.rotation((float)Math.PI));
                font.drawInBatch(component, h, 0, 553648127, false, matrix4f, multiBufferSource, Font.DisplayMode.SEE_THROUGH, k, LightTexture.FULL_BRIGHT);
                font.drawInBatch(component, h, 0, -1, false, matrix4f, multiBufferSource, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BlockEntityMinoTable blockEntity) {
        return true;
    }
}
