/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.renderer;

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
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.content.entity.ErrorAnglerEntity;

import java.awt.Color;
import java.util.UUID;

public class ErrorAnglerEntityRenderer extends LivingEntityRenderer<ErrorAnglerEntity, PlayerModel<ErrorAnglerEntity>> {
    private static final UUID DEFAULT_SKIN = UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2");

    private final PlayerModel<ErrorAnglerEntity> slimModel;
    private final ItemInHandLayer<ErrorAnglerEntity, PlayerModel<ErrorAnglerEntity>> handLayer;

    public ErrorAnglerEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(TMMModelLayers.PLAYER_BODY), false), 0.5F);
        this.slimModel = new PlayerModel<>(ctx.bakeLayer(TMMModelLayers.PLAYER_BODY_SLIM), true);
        this.handLayer = new ItemInHandLayer<>(this, ctx.getItemInHandRenderer());
        this.addLayer(this.handLayer);
    }

    @Override
    protected void renderNameTag(ErrorAnglerEntity entity, Component component, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick) {
    }

    private PlayerModel<ErrorAnglerEntity> pickModel(ErrorAnglerEntity entity) {
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
    public void render(ErrorAnglerEntity entity, float yaw, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light) {
        PlayerModel<ErrorAnglerEntity> model = pickModel(entity);
        model.setAllVisible(true);
        Minecraft client = Minecraft.getInstance();
        boolean bodyVisible = this.isBodyVisible(entity);
        boolean translucent = !bodyVisible && !entity.isInvisibleTo(client.player);
        boolean glowing = client.shouldEntityAppearGlowing(entity);
        RenderType renderLayer = this.getRenderType(entity, bodyVisible, translucent, glowing);

        matrices.pushPose();
        doRender(entity, tickDelta, matrices, vertexConsumers, light, model, renderLayer, translucent);
        matrices.popPose();
    }

    private void doRender(ErrorAnglerEntity livingEntity, float g, PoseStack matrixStack,
            MultiBufferSource vertexConsumerProvider, int light, PlayerModel<ErrorAnglerEntity> model,
            RenderType renderLayer, boolean invisibleToOthers) {
        matrixStack.pushPose();
        model.attackTime = 0;
        model.riding = true;
        model.young = false;

        float bodyYaw = Mth.rotLerp(g, livingEntity.yBodyRotO, livingEntity.yBodyRot);
        float headYaw = Mth.rotLerp(g, livingEntity.yHeadRotO, livingEntity.yHeadRot);
        float netHead = headYaw - bodyYaw;
        float xRot = Mth.lerp(g, livingEntity.xRotO, livingEntity.getXRot());
        float scale = livingEntity.getScale();
        matrixStack.scale(scale, scale, scale);
        this.setupRotations(livingEntity, matrixStack, 0, bodyYaw, g, scale);
        matrixStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(livingEntity, matrixStack, g);
        matrixStack.translate(0.0F, -1.501F, 0.0F);

        model.prepareMobModel(livingEntity, 0, 0, g);
        model.setupAnim(livingEntity, 0, 0, 0, netHead, xRot);

        if (renderLayer != null) {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            int overlay = getOverlayCoords(livingEntity, 0);
            int color = invisibleToOthers ? 654311423 : new Color(1f, 1f, 1f, 1f).getRGB();
            model.renderToBuffer(matrixStack, vertexConsumer, light, overlay, color);

            model.setAllVisible(false);
            model.head.visible = true;
            model.hat.visible = true;
            model.renderToBuffer(matrixStack, vertexConsumer, light, overlay, 0xFF000000);
            model.setAllVisible(true);
        }
        this.handLayer.render(matrixStack, vertexConsumerProvider, light, livingEntity, 0, 0, g, 0, netHead, xRot);
        matrixStack.popPose();
    }

    @Override
    protected void scale(ErrorAnglerEntity entity, PoseStack matrices, float amount) {
        float g = 0.9375F;
        matrices.scale(g, g, g);
    }

    @Override
    public ResourceLocation getTextureLocation(ErrorAnglerEntity entity) {
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
