package org.agmas.noellesroles.client.renderer;

import java.awt.Color;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.wifi.starrailexpress.client.model.TMMModelLayers;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.content.entity.UndeadEntity;

/**
 * 亡灵实体渲染器：与傀儡本体（{@link PuppeteerBodyEntityRenderer}）同款，
 * 直接以玩家模型渲染死者皮肤并驱动行走动画；区别在于叠加更强的白色覆盖，
 * 使皮肤显得苍白（亡灵感）。灰紫雾气仍由服务端粒子表现。
 */
public class UndeadEntityRenderer extends LivingEntityRenderer<UndeadEntity, PlayerModel<UndeadEntity>> {

    private static final UUID DEFAULT_SKIN = UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2");
    /** 苍白程度：白色覆盖比例（0 = 原皮肤，1 = 纯白）。傀儡本体为 0.1，亡灵更白。 */
    private static final float PALE_OVERLAY = 0.4F;

    /** 纤细（Alex）模型；宽臂（Steve）模型为 super 持有的 this.model。 */
    private final PlayerModel<UndeadEntity> slimModel;

    public UndeadEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(TMMModelLayers.PLAYER_BODY), false), 0.5F);
        this.slimModel = new PlayerModel<>(ctx.bakeLayer(TMMModelLayers.PLAYER_BODY_SLIM), true);
    }

    /** 亡灵不显示名牌。 */
    @Override
    protected void renderNameTag(UndeadEntity entity, Component component, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick) {
    }

    /** 依据死者皮肤的模型类型选择宽臂 / 纤细玩家模型。 */
    private PlayerModel<UndeadEntity> pickModel(UndeadEntity entity) {
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null) {
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            if (info != null && info.getSkin().model() == PlayerSkin.Model.SLIM) {
                return this.slimModel;
            }
        }
        return this.model;
    }

    @Override
    public void render(UndeadEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        // 剩余 10 秒闪烁：隔帧不渲染
        if (entity.isFlickering() && (entity.tickCount % 10) < 4) {
            return;
        }
        PlayerModel<UndeadEntity> model = pickModel(entity);
        model.setAllVisible(true);

        Minecraft client = Minecraft.getInstance();
        boolean bl = this.isBodyVisible(entity);
        boolean bl2 = !bl && !entity.isInvisibleTo(client.player);
        boolean bl3 = client.shouldEntityAppearGlowing(entity);
        RenderType renderLayer = this.getRenderType(entity, bl, bl2, bl3);

        matrices.pushPose();
        doRender(entity, tickDelta, matrices, vertexConsumers, light, model, renderLayer, bl2);
        matrices.popPose();
    }

    private void doRender(UndeadEntity livingEntity, float g, PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider, int light, PlayerModel<UndeadEntity> model,
            RenderType renderLayer, boolean invisibleToOthers) {
        matrixStack.pushPose();
        model.attackTime = this.getAttackAnim(livingEntity, g);
        model.riding = livingEntity.isPassenger();
        model.young = livingEntity.isBaby();

        float h = Mth.rotLerp(g, livingEntity.yBodyRotO, livingEntity.yBodyRot);
        float j = Mth.rotLerp(g, livingEntity.yHeadRotO, livingEntity.yHeadRot);
        float k = j - h;
        float m = Mth.lerp(g, livingEntity.xRotO, livingEntity.getXRot());

        float lx = livingEntity.getScale();
        matrixStack.scale(lx, lx, lx);
        float n = this.getAttackAnim(livingEntity, g);
        this.setupRotations(livingEntity, matrixStack, n, h, g, lx);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(livingEntity, matrixStack, g);
        matrixStack.translate(0.0F, -1.501F, 0.0F);

        // 行走动画：使用实体自身的 limbSwing，迈腿而非平滑滑行
        float o = 0.0F;
        float p = 0.0F;
        if (!livingEntity.isPassenger() && livingEntity.isAlive()) {
            o = livingEntity.walkAnimation.speed(g);
            p = livingEntity.walkAnimation.position(g);
            if (o > 1.0F) {
                o = 1.0F;
            }
        }

        model.prepareMobModel(livingEntity, p, o, g);
        model.setupAnim(livingEntity, p, o, n, k, m);

        if (renderLayer != null) {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            // 较强的白色覆盖让皮肤偏白（苍白亡灵感）
            int overlay = getOverlayCoords(livingEntity, this.getWhiteOverlayProgress(livingEntity, g));
            int color = invisibleToOthers ? 654311423 : new Color(1f, 1f, 1f, 1f).getRGB();
            model.renderToBuffer(matrixStack, vertexConsumer, light, overlay, color);
        }
        matrixStack.popPose();
    }

    @Override
    protected void scale(UndeadEntity entity, PoseStack matrices, float amount) {
        float g = 0.9375F;
        matrices.scale(g, g, g);
    }

    /** 恒定的白色覆盖：让亡灵皮肤整体偏白。 */
    @Override
    protected float getWhiteOverlayProgress(UndeadEntity livingEntity, float f) {
        return PALE_OVERLAY;
    }

    @Override
    public ResourceLocation getTextureLocation(UndeadEntity entity) {
        UUID skinUuid = entity.getSkinUuid();
        if (skinUuid != null) {
            PlayerInfo entry = ClientSkinCache.getCachedPlayerInfo(skinUuid);
            if (entry != null) {
                return entry.getSkin().texture();
            }
            return DefaultPlayerSkin.get(skinUuid).texture();
        }
        return DefaultPlayerSkin.get(DEFAULT_SKIN).texture();
    }
}
