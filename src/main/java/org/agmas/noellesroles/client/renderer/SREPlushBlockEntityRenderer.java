package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.CustomPlayerPlushBlock;
import org.agmas.noellesroles.content.block.SREPlushBlock;
import org.agmas.noellesroles.content.block_entity.SREPlushBlockEntity;
import org.jetbrains.annotations.Nullable;

public class SREPlushBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private final BlockRenderDispatcher renderer;

    public SREPlushBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.renderer = ctx.getBlockRenderDispatcher();
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
        RenderType renderType = null;
        if (blockEntity instanceof SREPlushBlockEntity plush) {
            if (plush.getCustomTexture() != null) {
                renderType = getRenderType(plush.getCustomTexture());
            } else {
                ResolvableProfile profile = plush.getOwnerProfile();
                renderType = getRenderType(profile);
            }
        }

        Direction facing = state.hasProperty(SREPlushBlock.FACING) ? state.getValue(SREPlushBlock.FACING)
                : Direction.NORTH;
        facing = facing.getOpposite();

        poseStack.pushPose();
        // 挤压动画（绕方块底面，水平鼓起、竖直压扁）
        poseStack.scale(1, 1F - squash, 1);
        poseStack.translate(0.5, 0, 0.5);
        poseStack.scale(1F + squash / 2F, 1, 1F + squash / 2F);
        poseStack.translate(-0.5, 0, -0.5);
        // 实体模型 -> 方块空间：移到方块顶面中心并翻转 Y（ModelPart 内部已 ÷16）
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, -1.5, -0.5);

        // poseStack.scale(1, -1, 1);

        CustomPlayerPlushModel.render(poseStack, consumer.getBuffer(renderType), light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private float computeSquash(T blockEntity, float delta) {
        double squish = blockEntity instanceof SREPlushBlockEntity plushie ? plushie.squash : 0;
        double lastSquish = squish * 3;
        return (float) Math.pow(1.0 - 1.0 / (1.0 + Mth.lerp((double) delta, lastSquish, squish)), 2.0);
    }

    public static RenderType getRenderType(@Nullable ResourceLocation res) {
        if (res == null)
            return null;
        return RenderType.entityTranslucent(res);
    }

    public static RenderType getRenderType(@Nullable ResolvableProfile resolvableProfile) {
        ResourceLocation resourceLocation = SRE.id("textures/entity/custom_psycho/th_sariel.png");
        if (resolvableProfile != null && resolvableProfile.gameProfile() != null) {
            SkinManager skinManager = Minecraft.getInstance().getSkinManager();
            return RenderType.entityTranslucent(skinManager.getInsecureSkin(resolvableProfile.gameProfile()).texture());
        } else {
            return RenderType.entityCutoutNoCullZOffset(resourceLocation);
        }
    }
}
