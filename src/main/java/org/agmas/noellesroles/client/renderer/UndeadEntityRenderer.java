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
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
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

import java.util.UUID;

/**
 * 亡灵使用独立的玩家网格：保留完整躯干、外套、头部、双臂及 slim 差异，仅隐藏双腿。
 * 整体仅绘制一次半透明主体，避免基类不透明主体叠加后使 alpha 失效。
 * 苍白覆盖、灰紫雾气与寿命末期闪烁保持不变；不修改共享 NPC 模型层。
 */
public class UndeadEntityRenderer extends LivingEntityRenderer<UndeadEntity, PlayerModel<UndeadEntity>> {

    private static final UUID DEFAULT_SKIN = UUID.fromString("7833c811-436e-40c4-868a-ffb1073f48a2");
    /** 苍白程度：白色覆盖比例（0 = 原皮肤，1 = 纯白）。傀儡本体为 0.1，亡灵更白。 */
    private static final float PALE_OVERLAY = 0.4F;

    /** 纤细（Alex）模型；宽臂（Steve）模型为 super 持有的 this.model。 */
    private final PlayerModel<UndeadEntity> slimModel;

    public UndeadEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, createGhostModel(false), 0.0F);
        this.slimModel = createGhostModel(true);
    }

    /** 本地烘焙完整玩家网格；仅隐藏腿部，不裁短躯干或替换共享模型层。 */
    private static PlayerModel<UndeadEntity> createGhostModel(boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        return new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), slim);
    }

    /** 亡灵不显示名牌。 */
    @Override
    protected void renderNameTag(UndeadEntity entity, Component component, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick) {
    }

    /** 依据死者皮肤的模型类型选择宽臂 / 纤细玩家模型。 */
    private PlayerModel<UndeadEntity> pickModel(UndeadEntity entity) {
        UUID skinUuid = entity.getSkinUuid();
        PlayerInfo info = skinUuid == null ? null : ClientSkinCache.getCachedPlayerInfo(skinUuid);
        PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(skinUuid == null ? DEFAULT_SKIN : skinUuid);
        return skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.model;
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
        // 直接选半透明管线；不可调用 super.render 再叠一层幽灵，否则底层仍不透明。
        RenderType renderLayer = bl || bl2 ? RenderType.entityTranslucent(getTextureLocation(entity))
                : bl3 ? RenderType.outline(getTextureLocation(entity)) : null;

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

        // 保留双臂的移动动画，双腿与裤腿在动画计算后始终隐藏。
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
        model.leftLeg.visible = false;
        model.rightLeg.visible = false;
        model.leftPants.visible = false;
        model.rightPants.visible = false;

        if (renderLayer != null) {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(renderLayer);
            // 较强的白色覆盖让皮肤偏白（苍白亡灵感）
            int overlay = getOverlayCoords(livingEntity, this.getWhiteOverlayProgress(livingEntity, g));
            int color = invisibleToOthers ? 0x26FFFFFF : 0x80FFFFFF; // 正常 alpha ≈ 0.50。
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
