package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.doctor4t.ratatouille.client.lib.render.helpers.Easing;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.model.entity.PlayerSkeletonEntityModel;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.awt.*;

public class PlayerBodyEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends LivingEntityRenderer<PlayerBodyEntity, PlayerModel<PlayerBodyEntity>> {
    public static final ResourceLocation DEFAULT_TEXTURE = SRE.watheId("textures/entity/player_body_default.png");
    private static final ResourceLocation SKELETON_TEXTURE = SRE.watheId("textures/entity/player_skeleton.png");

    protected PlayerSkeletonEntityModel<PlayerBodyEntity> skeletonModel;

    public PlayerBodyEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? TMMModelLayers.PLAYER_BODY_SLIM : TMMModelLayers.PLAYER_BODY), slim), 0F);
        skeletonModel = new PlayerSkeletonEntityModel<>(ctx.bakeLayer(TMMModelLayers.PLAYER_SKELETON));
    }

    public void render(PlayerBodyEntity playerBodyEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light) {
        this.setModelPose();

        matrixStack.pushPose();
        float clamp = Mth.clamp((float) (playerBodyEntity.tickCount - GameConstants.TIME_TO_DECOMPOSITION) / GameConstants.DECOMPOSING_TIME, 0, GameConstants.TIME_TO_DECOMPOSITION + GameConstants.DECOMPOSING_TIME);
        float ease = Easing.CUBIC_IN.ease(clamp, 0, -1, 1);
        final var moodComponent = SREClient.moodComponent;
        if (moodComponent==null)return;
        if (ease > -1) {
            matrixStack.translate(0, ease, 0);
            float alpha = moodComponent.isLowerThanDepressed() ? Mth.lerp(Mth.clamp(Easing.SINE_IN.ease(Math.min(1f, (float) playerBodyEntity.tickCount / 100f), 0, 1, 1), 0, 1), 1f, 0f) : 1f;
            this.renderBody(playerBodyEntity, f, g, matrixStack, vertexConsumerProvider, light, alpha);
        }
        matrixStack.popPose();

        renderSkeleton(playerBodyEntity, f, g, matrixStack, vertexConsumerProvider, light, moodComponent.isLowerThanDepressed() ? 0f : 1f);
    }

    public void renderBody(PlayerBodyEntity livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, float alpha) {
        boolean bl = this.isBodyVisible(livingEntity);
        Minecraft client = Minecraft.getInstance();
        boolean bl2 = !bl && !livingEntity.isInvisibleTo(client.player);
        boolean bl3 = client.shouldEntityAppearGlowing(livingEntity);
        RenderType bodyRenderLayer = this.getRenderType(livingEntity, bl, bl2, bl3);

        render(livingEntity, f, g, matrixStack, vertexConsumerProvider, light, this.model, bodyRenderLayer, 1f, alpha);
    }

    public void renderSkeleton(PlayerBodyEntity livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, float alpha) {
        render(livingEntity, f, g, matrixStack, vertexConsumerProvider, light, this.skeletonModel, this.getSkeletonRenderLayer(), .95f, alpha);
    }

    public void render(PlayerBodyEntity livingEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int light, HumanoidModel<PlayerBodyEntity> model, RenderType renderLayer, float scale, float alpha) {
        if (alpha > 0) {
            matrixStack.pushPose();
            this.model.attackTime = this.getAttackAnim(livingEntity, g);
            this.model.riding = livingEntity.isPassenger();
            this.model.young = livingEntity.isBaby();

            float h = Mth.rotLerp(g, livingEntity.yBodyRotO, livingEntity.yBodyRot);
            float j = Mth.rotLerp(g, livingEntity.yHeadRotO, livingEntity.yHeadRot);
            float k = j - h;

            float m = Mth.lerp(g, livingEntity.xRotO, livingEntity.getXRot());
            if (isEntityUpsideDown(livingEntity)) {
                m *= -1.0F;
                k *= -1.0F;
            }

            float lx = livingEntity.getScale();
            matrixStack.scale(lx, lx, lx);
            float n = this.getAttackAnim(livingEntity, g);
            this.setupRotations(livingEntity, matrixStack, n, h, g, lx);
            matrixStack.scale(-1.0F, -1.0F, 1.0F);
            this.scale(livingEntity, matrixStack, g);
            matrixStack.translate(0.0F, -1.501F, 0.0F);
            float o = 0.0F;
            float p = 0.0F;
            if (!livingEntity.isPassenger() && livingEntity.isAlive()) {
                o = livingEntity.walkAnimation.speed(g);
                p = livingEntity.walkAnimation.position(g);
                if (livingEntity.isBaby()) {
                    p *= 3.0F;
                }

                if (o > 1.0F) {
                    o = 1.0F;
                }
            }

            model.prepareMobModel(livingEntity, p, o, g);
            model.setupAnim(livingEntity, p, o, n, k, m);
            Minecraft minecraftClient = Minecraft.getInstance();
            boolean bl = this.isBodyVisible(livingEntity);
            boolean bl2 = !bl && !livingEntity.isInvisibleTo(minecraftClient.player);
            if (renderLayer != null) {
                VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
                int q = getOverlayCoords(livingEntity, this.getWhiteOverlayProgress(livingEntity, g));
                matrixStack.pushPose();
                matrixStack.scale(scale, scale, scale);

                Color color = new Color(1f, 1f, 1f, alpha);
                model.renderToBuffer(matrixStack, vertexConsumer, light, q, bl2 ? 654311423 : color.getRGB());
                matrixStack.popPose();
            }

            matrixStack.popPose();
        }
    }

    private RenderType getSkeletonRenderLayer() {
        return this.model.renderType(SKELETON_TEXTURE);
    }

    private void setModelPose() {
        PlayerModel<PlayerBodyEntity> playerEntityModel = this.getModel();
        playerEntityModel.setAllVisible(false);
        playerEntityModel.head.visible = true;
        playerEntityModel.hat.visible = true;
        playerEntityModel.setAllVisible(true);
        playerEntityModel.hat.visible = true;
        playerEntityModel.jacket.visible = true;
        playerEntityModel.leftPants.visible = true;
        playerEntityModel.rightPants.visible = true;
        playerEntityModel.leftSleeve.visible = true;
        playerEntityModel.rightSleeve.visible = true;
        skeletonModel.setAllVisible(true);
        skeletonModel.young = false;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerBodyEntity playerBodyEntity) {
        PlayerInfo playerListEntry = SREClient.PLAYER_ENTRIES_CACHE.get(playerBodyEntity.getPlayerUuid());
        if (playerListEntry != null) {
            return playerListEntry.getSkin().texture();
        } else {
            return DEFAULT_TEXTURE;
        }
    }


    @Override
    protected void renderNameTag(PlayerBodyEntity entity, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f) {

    }

    @Override
    protected void setupRotations(PlayerBodyEntity livingEntity, PoseStack poseStack,float animationProgress, float bodyYaw, float tickDelta, float scale) {
        int animTickEnd = 20;
        float t = Math.min(livingEntity.tickCount + tickDelta, animTickEnd) / animTickEnd;
        float animProgress = Easing.BOUNCE_OUT.ease(t, 0, 1, 1);

        poseStack.mulPose(Axis.YP.rotationDegrees(90 - bodyYaw));
        poseStack.translate(1F, 0f, 0f);
        poseStack.translate(0F, animProgress * 0.15f, 0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(animProgress * this.getFlipDegrees(livingEntity)));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
    }



    @Override
    protected void scale(PlayerBodyEntity entity, PoseStack matrices, float amount) {
        float g = 0.9375F;
        matrices.scale(g, g, g);
    }

    @Override
    protected float getAttackAnim(PlayerBodyEntity livingEntity, float f) {
        return 0f;
    }



    @Override
    protected float getWhiteOverlayProgress(PlayerBodyEntity livingEntity, float f) {
        return 0.1f;
    }


}
