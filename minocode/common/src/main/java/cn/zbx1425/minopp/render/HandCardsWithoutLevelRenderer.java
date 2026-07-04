package cn.zbx1425.minopp.render;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.block.BlockMinoTable;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.platform.RegistryObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class HandCardsWithoutLevelRenderer extends BlockEntityWithoutLevelRenderer {

    public static RegistryObject<HandCardsWithoutLevelRenderer> INSTANCE = new RegistryObject<>(() -> new HandCardsWithoutLevelRenderer(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()));

    public HandCardsWithoutLevelRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher, EntityModelSet entityModelSet) {
        super(blockEntityRenderDispatcher, entityModelSet);
    }

    private static final RegistryObject<ItemStack> HAND_CARDS_MODEL_PLACEHOLDER = new RegistryObject<>(() -> new ItemStack(Mino.ITEM_HAND_CARDS_MODEL_PLACEHOLDER.get()));

    @Override
    public void renderByItem(ItemStack itemStack, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, int packedOverlay) {
        switch (itemDisplayContext) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                return;
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                ItemHandCards.CardGameBindingComponent gameBinding = itemStack.get(Mino.DATA_COMPONENT_TYPE_CARD_GAME_BINDING.get());
                if (gameBinding == null) return;
                BlockPos tablePos = gameBinding.tablePos();
                ClientLevel level = Minecraft.getInstance().level;
                BlockState blockState = level.getBlockState(tablePos);
                tablePos = BlockMinoTable.getCore(blockState, tablePos);
                if (level.getBlockEntity(tablePos) instanceof BlockEntityMinoTable tableEntity) {
                    if (tableEntity.game == null) return;
                    CardPlayer realPlayer = tableEntity.game.players.stream().filter(p -> p.uuid.equals(gameBinding.bearerId()))
                            .findFirst().orElse(null);
                    if (realPlayer == null) return;
                    poseStack.popPose();
                    poseStack.pushPose();
                    poseStack.translate(0, 0, 0.18);
                    for (int k = 0; k < realPlayer.hand.size(); k++) {
                        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                        itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), itemDisplayContext, true, poseStack, multiBufferSource, packedLight, packedOverlay,
                                itemRenderer.getModel(HAND_CARDS_MODEL_PLACEHOLDER.get(), null, null, 0));
                        poseStack.translate(0, 0.02, 0.08);
                    }

                    // Render arrow texture
                    if (tableEntity.game.currentPlayerIndex == tableEntity.game.players.indexOf(realPlayer)) {
                        poseStack.translate(0, 0.3, 0.3);
                        poseStack.mulPose(Axis.XP.rotationDegrees(-110f));
                        poseStack.pushPose();
                        VertexConsumer buffer = multiBufferSource.getBuffer(RenderType.entityCutout(Mino.id("textures/gui/arrow_down.png")));
                        float v0 = ((int)(System.currentTimeMillis() / 100L) % 5) * 0.2f;
                        float v1 = v0 + 0.2f;
                        // Transform must be somehow messed up but it works so I'm not going to fix it
                        poseStack.mulPose(Axis.YP.rotationDegrees(45));
                        poseStack.scale(0.2f, 0.2f, 1);
                        buffer
                                .addVertex(poseStack.last(), -1, 1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), -1, -1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), 1, -1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), 1, 1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF);
                        poseStack.popPose();
                        poseStack.pushPose();
                        poseStack.mulPose(Axis.YP.rotationDegrees(-45));
                        poseStack.scale(0.2f, 0.2f, 1);
                        buffer
                                .addVertex(poseStack.last(), -1, 1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), -1, -1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), 1, -1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF)
                                .addVertex(poseStack.last(), 1, 1, 0).setNormal(poseStack.last(), 0, -1, 0)
                                .setUv(1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setColor(0xFFFFFFFF);
                        poseStack.popPose();
                    }

                    poseStack.popPose();
                    poseStack.pushPose();
                }
            }
            case GUI -> {
                poseStack.popPose();
                poseStack.mulPose(Axis.ZP.rotationDegrees(15f));
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), itemDisplayContext, true, poseStack, multiBufferSource, LightTexture.FULL_BRIGHT, packedOverlay,
                        itemRenderer.getModel(HAND_CARDS_MODEL_PLACEHOLDER.get(), null, null, 0));
                poseStack.pushPose();
            }
            default -> {
                poseStack.popPose();
                ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
                itemRenderer.render(HAND_CARDS_MODEL_PLACEHOLDER.get(), itemDisplayContext, true, poseStack, multiBufferSource, packedLight, packedOverlay,
                        itemRenderer.getModel(HAND_CARDS_MODEL_PLACEHOLDER.get(), null, null, 0));
                poseStack.pushPose();
            }
        }
    }
}
