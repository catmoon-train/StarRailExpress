package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import java.awt.*;
import java.util.UUID;

/**
 * 傀儡本体实体渲染器
 * 
 * 使用玩家皮肤渲染傀儡本体
 */
public class PuppeteerBodyEntityRenderer<T extends LivingEntity, M extends EntityModel<T>>
        extends LivingEntityRenderer<PuppeteerBodyEntity, PlayerModel<PuppeteerBodyEntity>> {
    static final int MAX_DISTANCE = 36 * 36;
    static final ResourceLocation DEFAULT_TEXTURE = DefaultPlayerSkin.getDefaultTexture();

    public PuppeteerBodyEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(slim ? TMMModelLayers.PLAYER_BODY_SLIM : TMMModelLayers.PLAYER_BODY),
                slim), 0F);
    }

    @Override
    protected void renderNameTag(PuppeteerBodyEntity entity, Component component, PoseStack poseStack,
            MultiBufferSource multiBufferSource, int i, float f) {

    }

    @Override
    public boolean shouldRender(PuppeteerBodyEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        // 1. 距离剔除（原本在 render() 里，提前到此处）
        Minecraft client = Minecraft.getInstance();
        if (client.player == null)
            return false;
        if (entity.distanceToSqr(client.player) >= MAX_DISTANCE)
            return false;

        // 2. 视锥体剔除：实体包围盒不在相机视野内时跳过
        AABB aabb = entity.getBoundingBoxForCulling();
        if (!frustum.isVisible(aabb))
            return false;

        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    private void setModelPose() {
        PlayerModel<PuppeteerBodyEntity> playerEntityModel = this.getModel();
        playerEntityModel.setAllVisible(true);
        playerEntityModel.head.visible = true;
        playerEntityModel.hat.visible = true;
        playerEntityModel.jacket.visible = true;
        playerEntityModel.leftPants.visible = true;
        playerEntityModel.rightPants.visible = true;
        playerEntityModel.leftSleeve.visible = true;
        playerEntityModel.rightSleeve.visible = true;
    }

    @Override
    public void render(PuppeteerBodyEntity playerBodyEntity, float f, float g, PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider, int light) {
        this.setModelPose();
        matrixStack.pushPose();
        // final var moodComponent = SREClient.moodComponent;
        float alpha = 1f;
        this.renderBody(playerBodyEntity, f, g, matrixStack, vertexConsumerProvider, light, alpha);
        matrixStack.popPose();
    }

    public void renderBody(PuppeteerBodyEntity livingEntity, float f, float g, PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider, int light, float alpha) {
        boolean bl = this.isBodyVisible(livingEntity);
        Minecraft client = Minecraft.getInstance();
        boolean bl2 = !bl && !livingEntity.isInvisibleTo(client.player);
        boolean bl3 = client.shouldEntityAppearGlowing(livingEntity);
        RenderType bodyRenderLayer = this.getRenderType(livingEntity, bl, bl2, bl3);

        doRender(livingEntity, f, g, matrixStack, vertexConsumerProvider, light, this.model, bodyRenderLayer, 1f,
                alpha);
    }

    public void doRender(PuppeteerBodyEntity livingEntity, float f, float g, PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider, int light, HumanoidModel<PuppeteerBodyEntity> model,
            RenderType renderLayer, float scale, float alpha) {
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

    @Override
    public ResourceLocation getTextureLocation(PuppeteerBodyEntity entity) {
        // 首先尝试通过 ownerUuid 从玩家列表获取皮肤
        UUID ownerUuid = entity.getOwnerUuid().orElse(null);
        if (ownerUuid == null) {
            return DEFAULT_TEXTURE;
        }
        if (SREClient.getLooseEndPenalty()) {
            return DEFAULT_TEXTURE;
        }
        Player owner = entity.getOwner();
        if (owner != null) {
            if(owner instanceof AbstractClientPlayer abp)
            return abp.getSkin().texture();
        } else {
            PlayerInfo playerListEntry = ClientSkinCache.getCachedPlayerInfo(ownerUuid);
            if (playerListEntry != null) {
                return playerListEntry.getSkin().texture();
            }
        }

        return DEFAULT_TEXTURE;
    }

    @Override
    protected void scale(PuppeteerBodyEntity entity, PoseStack matrices, float amount) {
        float g = 0.9375F;
        matrices.scale(g, g, g);
    }

    @Override
    protected float getAttackAnim(PuppeteerBodyEntity livingEntity, float f) {
        return 0f;
    }

    @Override
    protected float getWhiteOverlayProgress(PuppeteerBodyEntity livingEntity, float f) {
        return 0.1f;
    }
}