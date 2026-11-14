package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.SackGhostEffigyEntity;

/** 直接绘制本人皮肤的草人；近身时皮囊错裂，露出向前扑出的厉鬼。 */
public final class SackGhostEffigyRenderer extends EntityRenderer<SackGhostEffigyEntity> {
    private static PlayerModel<LivingEntity> wide;
    private static PlayerModel<LivingEntity> slim;

    public record Skin(ResourceLocation texture, boolean slim) {}
    public record Echo(Skin skin, float yaw, float headYaw, float pitch, float walk, float speed) {}

    public SackGhostEffigyRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.3F;
    }

    private static Skin entitySkin(SackGhostEffigyEntity entity) {
        Player owner = entity.getOwner();
        if (owner != null) return skin(owner);
        java.util.UUID uuid = entity.getOwnerUuid().orElse(new java.util.UUID(0, 0));
        var info = ClientSkinCache.getCachedPlayerInfo(uuid);
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (info == null && connection != null) info = connection.getPlayerInfo(uuid);
        PlayerSkin skin = info == null ? DefaultPlayerSkin.get(uuid) : info.getSkin();
        return new Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
    }

    public static Skin skin(Player player) {
        if (player instanceof AbstractClientPlayer clientPlayer) {
            PlayerSkin skin = clientPlayer.getSkin();
            return new Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
        }
        var info = player == null ? null : ClientSkinCache.getCachedPlayerInfo(player.getUUID());
        PlayerSkin skin = info == null ? DefaultPlayerSkin.get(player == null ? new java.util.UUID(0, 0) : player.getUUID()) : info.getSkin();
        return new Skin(skin.texture(), skin.model() == PlayerSkin.Model.SLIM);
    }

    public static Echo snapshot(Player owner) {
        return new Echo(skin(owner), owner == null ? 0 : owner.yBodyRot,
                owner == null ? 0 : owner.yHeadRot - owner.yBodyRot, owner == null ? 0 : owner.getXRot(),
                owner == null ? 0 : owner.walkAnimation.position(), owner == null ? 0 : owner.walkAnimation.speed());
    }

    private static PlayerModel<LivingEntity> model(boolean narrow) {
        if (narrow) {
            if (slim == null) slim = buildModel(true);
            return slim;
        }
        if (wide == null) wide = buildModel(false);
        return wide;
    }

    private static PlayerModel<LivingEntity> buildModel(boolean narrow) {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, narrow);
        splitPart(mesh.getRoot().getChild("head"), 0, 0, -8, -4, 8, 8, 0);
        splitPart(mesh.getRoot().getChild("hat"), 32, 0, -8, -4, 8, 8, 0.5F);
        splitPart(mesh.getRoot().getChild("body"), 16, 16, 0, -2, 12, 4, 0);
        splitPart(mesh.getRoot().getChild("jacket"), 16, 32, 0, -2, 12, 4, 0.25F);
        return new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), narrow);
    }

    private static void splitPart(PartDefinition parent, int u, int v, float y, float z, float height, float depth, float extra) {
        for (int i = 0; i < 2; i++) parent.addOrReplaceChild("sack_half" + i,
                CubeListBuilder.create().texOffs(u + i * 4, v).addBox(-4 + i * 4, y, z, 4, height, depth, new CubeDeformation(extra)), PartPose.ZERO);
    }

    private static void split(net.minecraft.client.model.geom.ModelPart part, float amount) {
        part.skipDraw = amount > 0.01F;
        for (int i = 0; i < 2; i++) {
            var half = part.getChild("sack_half" + i);
            half.visible = part.skipDraw;
            half.x = (i == 0 ? -1 : 1) * amount * 2.4F;
            half.zRot = (i == 0 ? -1 : 1) * amount * 0.12F;
        }
    }

    @Override
    public boolean shouldRender(SackGhostEffigyEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) <= 48 * 48 && super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(SackGhostEffigyEntity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        partial = SackGhostClient.visualPartial(partial);
        Player owner = entity.getOwner();
        float nearest = 4;
        // 客户端不掌握服务端索敌阵营；这里只按近身距离驱动皮囊动画，不参与选敌。
        for (Player player : entity.level().players()) {
            if (player != owner && entity.getOwnerUuid().filter(player.getUUID()::equals).isEmpty()
                    && player.isAlive() && !player.isSpectator()) nearest = Math.min(nearest, entity.distanceTo(player));
        }
        float split = Mth.clamp((3.2F - nearest) / 1.8F, 0, 1);
        Echo appearance = new Echo(entitySkin(entity), Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot),
                0, entity.getXRot(), entity.walkAnimation.position(partial), entity.walkAnimation.speed(partial));
        renderSkin(appearance, poses, buffers, light, 1, split);
        if (split > 0.05F) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(-appearance.yaw()));
            poses.translate(0, 0.1, 0.55 * split);
            SackGhostRenderer.draw(poses, buffers, light, SackGhostClient.visualTime(partial), 0, -15,
                    0.85F + split * 0.15F, split, split * 0.8F, split);
            poses.popPose();
        }
    }

    public static void renderEcho(Echo echo, PoseStack poses, MultiBufferSource buffers, int light, float alpha) {
        renderSkin(echo, poses, buffers, light, alpha, 0);
    }

    private static void renderSkin(Echo echo, PoseStack poses, MultiBufferSource buffers,
                                   int light, float alpha, float split) {
        PlayerModel<LivingEntity> model = model(echo.skin().slim());
        model.setAllVisible(true);
        model.young = false;
        model.riding = false;
        model.crouching = false;
        model.attackTime = 0;
        model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        // 固定残影只恢复快照姿态，不依赖仍在线的 owner，也不递归渲染玩家。
        for (var part : new net.minecraft.client.model.geom.ModelPart[]{model.head, model.hat, model.body, model.jacket,
                model.leftArm, model.rightArm, model.leftSleeve, model.rightSleeve, model.leftLeg, model.rightLeg, model.leftPants, model.rightPants}) part.resetPose();
        float swing = Mth.cos(echo.walk() * 0.6662F) * echo.speed();
        model.head.yRot = echo.headYaw() * Mth.DEG_TO_RAD;
        model.head.xRot = echo.pitch() * Mth.DEG_TO_RAD;
        model.head.zRot = -split * 0.4F;
        model.head.x -= split * 2.5F;
        model.head.y -= split * 2;
        model.body.zRot = split * 0.18F;
        model.body.x += split * 1.5F;
        model.body.xScale = 1 - split * 0.25F;
        model.leftArm.xRot = -swing - split * 1.7F;
        model.rightArm.xRot = swing - split * 1.7F;
        model.leftArm.zRot = -split * 0.65F;
        model.rightArm.zRot = split * 0.65F;
        model.leftArm.x += split * 2;
        model.rightArm.x -= split * 2;
        model.leftLeg.xRot = swing * 1.4F;
        model.rightLeg.xRot = -swing * 1.4F;
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        split(model.head, split); split(model.hat, split);
        split(model.body, split); split(model.jacket, split);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(180 - echo.yaw()));
        poses.scale(-0.9375F, -0.9375F, 0.9375F);
        poses.translate(0, -1.501, 0);
        model.renderToBuffer(poses, buffers.getBuffer(RenderType.entityTranslucent(echo.skin().texture())), light,
                OverlayTexture.NO_OVERLAY, (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | 0xFFFFFF);
        poses.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(SackGhostEffigyEntity entity) {
        return entitySkin(entity).texture();
    }
}
