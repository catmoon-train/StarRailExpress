package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.content.block.CustomPlayerPlushBlock;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wifi.starrailexpress.client.data.PlayerPlushSkinCache;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SREPlushBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation FALLBACK_SKIN = OnGettingPlayerSkin.PlayerSkinResult.steveWide().texture;

    private final BlockRenderDispatcher renderer;
    private final CustomPlayerPlushModel customModel;

    public SREPlushBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.renderer = ctx.getBlockRenderDispatcher();
        this.customModel = new CustomPlayerPlushModel(ctx.bakeLayer(TMMModelLayers.CUSTOM_PLAYER_PLUSH));
    }

    @Override
    public void render(T blockEntity, float delta, PoseStack poseStack, MultiBufferSource consumer, int light,
            int overlay) {
        BlockState state = blockEntity.getBlockState();
        float squash = computeSquash(blockEntity, delta);
        if (state.getBlock() instanceof CustomPlayerPlushBlock) {
            renderCustomPlayerPlush(blockEntity, state, squash, poseStack, consumer, light, overlay);
            return;
        }
        renderBakedPlush(state, squash, poseStack, consumer, light, overlay);
    }

    /** 原版 plush：渲染烘焙方块模型，带挤压动画。 */
    private void renderBakedPlush(BlockState state, float squash, PoseStack poseStack, MultiBufferSource consumer,
            int light, int overlay) {
        poseStack.pushPose();
        poseStack.scale(1, 1F - squash, 1);
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        var bakedModel = this.renderer.getBlockModel(state);
        this.renderer.getModelRenderer().renderModel(poseStack.last(),
                consumer.getBuffer(ItemBlockRenderTypes.getRenderType(state, false)), state, bakedModel, 1F, 1F, 1F,
                light, overlay);
        poseStack.popPose();
    }

    /** 自定义玩家 plush：用绑定玩家的皮肤渲染 plush 形状模型。 */
    private void renderCustomPlayerPlush(T blockEntity, BlockState state, float squash, PoseStack poseStack,
            MultiBufferSource consumer, int light, int overlay) {
        String name = blockEntity instanceof SREPlushBlockEntity plush ? plush.getCustomPlayerName() : null;
        ResourceLocation texture = FALLBACK_SKIN;
        if (name != null && !name.isBlank()) {
            PlayerPlushSkinCache.Resolved resolved = PlayerPlushSkinCache.get(name);
            if (resolved != null && resolved.texture() != null) {
                texture = resolved.texture();
            }
        }

        Direction facing = state.hasProperty(SREPlushBlock.FACING) ? state.getValue(SREPlushBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        // 挤压动画（绕方块底面，水平鼓起、竖直压扁）
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1F - squash, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        // 实体模型 -> 方块空间：移到方块顶面中心并翻转 Y（ModelPart 内部已 ÷16）
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.scale(1, -1, 1);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

        RenderType renderType = RenderType.entityCutoutNoCull(texture);
        this.customModel.render(poseStack, consumer.getBuffer(renderType), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private float computeSquash(T blockEntity, float delta) {
        double squish = blockEntity instanceof SREPlushBlockEntity plushie ? plushie.squash : 0;
        double lastSquish = squish * 3;
        return (float) Math.pow(1.0 - 1.0 / (1.0 + Mth.lerp((double) delta, lastSquish, squish)), 2.0);
    }
}
