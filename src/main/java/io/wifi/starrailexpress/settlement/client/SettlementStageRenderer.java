package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.utils.RoleUtils;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** 不创建假玩家、不调用实体渲染器、setupAnim 或职业姿态钩子；仅直接绘制独立 PlayerModel。 */
public final class SettlementStageRenderer {
    private static PlayerModel<Player> wide, slim;
    private static Object models;
    private SettlementStageRenderer() { }

    static void invalidate() { wide = slim = null; models = null; }

    static void render(WorldRenderContext context) {
        if (!SettlementClient.isPlaying() || SettlementClient.snapshot() == null || context.matrixStack() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (wide == null || models != mc.getEntityModels()) {
            models = mc.getEntityModels();
            wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        var snapshot = SettlementClient.snapshot();
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double time = SettlementClient.tick() + partial;
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Vec3 camera = context.camera().getPosition();
        pose.pushPose();
        pose.translate(snapshot.x() + .5 - camera.x, snapshot.y() + 1 - camera.y, snapshot.z() + .5 - camera.z);
        for (var actor : SettlementClient.actors) {
            var info = ClientSkinCache.getCachedPlayerInfo(actor.fact.id());
            if (info == null && mc.getConnection() != null) info = mc.getConnection().getPlayerInfo(actor.fact.id());
            PlayerSkin skin = info == null ? DefaultPlayerSkin.get(actor.fact.id()) : info.getSkin();
            PlayerModel<Player> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
            boolean physical = actor.body.active;
            // 败方只以落下的尸体出现；不在舞台上先坐成一排，也不先升到天上。
            if (!actor.fact.winner() && !physical) continue;
            double x = actor.x, y = actor.y, z = actor.z;
            if (physical) {
                var middle = actor.body.nodes[1];
                x = middle.x(partial); y = middle.y(partial); z = middle.z(partial);
            } else {
                y += 10.3 * (1 - SettlementClient.smooth((time - 65 - Math.min(12, actor.order * 2)) / 65));
            }
            double action = Math.max(0, 1 - (time - actor.actionAt) / 22);
            boolean mvp = Objects.equals(actor.fact.id(), snapshot.mvp());
            pose.pushPose();
            pose.translate(x, y, z);
            if (physical) {
                var a = actor.body.nodes[0]; var b = actor.body.nodes[2];
                Vector3f up = new Vector3f((float) (b.x(partial) - a.x(partial)),
                        (float) (b.y(partial) - a.y(partial)), (float) (b.z(partial) - a.z(partial)));
                if (up.lengthSquared() > 1e-6) pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), up.normalize()));
                pose.mulPose(Axis.YP.rotation((float) actor.body.twist));
                pose.translate(0, -.9, 0);
            } else {
                pose.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(time * .055 + actor.order) * 2
                        * (1 - SettlementClient.smooth((time - 125) / 30)))));
            }
            // 此模型坐标朝 -Z，恰好面向舞台正面相机。
            pose.scale(-1, -1, 1); pose.translate(0, -1.501, 0);
            setPose(model, actor, time, action, mvp);
            float dark = actor.blackness;
            int r = (int) (255 - 230 * dark), g = (int) (255 - 235 * dark), b = (int) (255 - 235 * dark);
            int tint = 0xFF000000 | r << 16 | g << 8 | b;
            model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityTranslucent(skin.texture())),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
            if (mvp) SettlementCrown.render(pose, buffer, model.head);
            pose.popPose();

            if (actor.fact.winner() && time < 160) {
                float alpha = (float) (1 - SettlementClient.smooth((time - 135) / 25));
                VertexConsumer lines = buffer.getBuffer(RenderType.lines());
                line(pose, lines, x - .22, y + 1.45, z, x - .4, 12.8, z, .8f, .72f, .52f, alpha);
                line(pose, lines, x + .22, y + 1.45, z, x + .4, 12.8, z, .8f, .72f, .52f, alpha);
            }
            if (action > 0 || mvp) {
                VertexConsumer lines = buffer.getBuffer(RenderType.lines());
                float pulse = (float) (mvp ? .35 + .1 * Math.sin(time * .08) : action * .85);
                double ring = physical ? .5 : .62;
                for (int i = 0; i < 32; i++) {
                    double a = Math.PI * 2 * i / 32, bAngle = Math.PI * 2 * (i + 1) / 32;
                    double base = physical ? y - .85 : y + .03;
                    line(pose, lines, x + Math.cos(a) * ring, base, z + Math.sin(a) * ring,
                            x + Math.cos(bAngle) * ring, base, z + Math.sin(bAngle) * ring,
                            .83f, .68f, .22f, pulse);
                }
            }
            billboard(pose, buffer, actor, x, y, z, physical, mvp, action);
        }
        pose.popPose();
        buffer.endBatch();
    }

    private static void setPose(PlayerModel<Player> model, SettlementClient.ActorState actor,
                                double time, double action, boolean mvp) {
        for (var part : java.util.List.of(model.head, model.hat, model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg, model.jacket, model.leftSleeve, model.rightSleeve,
                model.leftPants, model.rightPants)) part.resetPose();
        model.setAllVisible(true); model.young = false; model.crouching = false; model.riding = false;
        model.attackTime = 0; model.swimAmount = 0;
        float ceremony = (float) SettlementClient.smooth((time - 140) / 25);
        float wave = (float) Math.sin((time - 140) * .18);
        model.head.xRot = -.06f; model.head.yRot = (float) Math.sin(time * .035) * .07f;
        model.leftArm.zRot = -.08f; model.rightArm.zRot = .08f;
        if (!actor.fact.winner()) {
            // 尸体四肢缓慢停摆；不再有“坐在舞台上”的待机姿势。
            float tumble = (float) Math.sin(time * .14 + actor.order) * .16f
                    * (float) (1 - SettlementClient.smooth((time - 185) / 65));
            model.head.xRot = .18f;
            model.leftArm.xRot = -.18f; model.rightArm.xRot = .15f;
            model.leftArm.zRot = -.35f + tumble; model.rightArm.zRot = .35f - tumble;
            model.leftLeg.xRot = .18f + tumble; model.rightLeg.xRot = -.18f - tumble;
        } else if (actor.fact.killer()) {
            model.head.xRot += .16f * ceremony;
            model.body.yRot = -.12f * ceremony;
            model.leftArm.xRot = -1.05f * ceremony; model.rightArm.xRot = -1.15f * ceremony;
            model.leftArm.yRot = .65f * ceremony; model.rightArm.yRot = -.65f * ceremony;
            model.leftArm.zRot = -.24f * ceremony; model.rightArm.zRot = .24f * ceremony;
            model.leftLeg.xRot = -.07f * ceremony; model.rightLeg.xRot = .07f * ceremony;
        } else if (neutralRole(actor)) {
            float bow = (float) Math.sin(SettlementClient.smooth((time - 145) / 65) * Math.PI) * ceremony;
            model.body.xRot = .5f * bow; model.head.xRot = .5f * bow;
            model.leftArm.xRot = model.rightArm.xRot = -.38f * bow;
        } else {
            model.rightArm.xRot = -2.6f * ceremony;
            model.rightArm.zRot = (.35f + wave * .22f) * ceremony;
            model.head.zRot = .06f * ceremony;
            if (mvp) {
                model.leftArm.xRot = -.7f * ceremony; model.leftArm.zRot = -.25f * ceremony;
                model.leftLeg.xRot = .1f * ceremony; model.rightLeg.xRot = -.1f * ceremony;
            }
        }
        if (actor.fact.winner() && action > 0 && SettlementClient.eventIndex() >= 0) {
            var event = SettlementClient.snapshot().events().get(SettlementClient.eventIndex());
            boolean source = actor.fact.id().equals(event.source());
            if (source) {
                model.rightArm.xRot = (float) (-1.5 - Math.sin(action * Math.PI) * .8);
                model.body.yRot += (float) Math.sin(action * Math.PI) * .2f;
            } else {
                model.head.xRot += (float) action * .5f;
                model.leftArm.zRot -= (float) action * .4f;
                model.rightArm.zRot += (float) action * .4f;
            }
        }
        // 外套/袖口/裤腿/帽子必须跟随手工关键帧，而不是 setupAnim 遗留状态。
        model.hat.copyFrom(model.head); model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm); model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg); model.rightPants.copyFrom(model.rightLeg);
    }

    private static boolean neutralRole(SettlementClient.ActorState actor) {
        ResourceLocation id = ResourceLocation.tryParse(actor.fact.role());
        var role = id == null ? null : io.wifi.starrailexpress.api.TMMRoles.getRole(id);
        if (role != null) return role.isNeutrals();
        int color = actor.fact.color(), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return (r > 140 && g > 110 && b < 100) || (r > 110 && b > 110 && g < 120);
    }

    private static void billboard(PoseStack pose, MultiBufferSource buffer, SettlementClient.ActorState actor,
                                  double x, double y, double z, boolean physical, boolean mvp, double action) {
        Minecraft mc = Minecraft.getInstance();
        // 紧凑队列的标签放在头顶；尸堆只给当前事件相关者显示，避免一团重叠文字。
        if (physical && action <= 0) return;
        Component name = Component.literal(actor.fact.name());
        ResourceLocation id = ResourceLocation.tryParse(actor.role);
        Component role = id == null
                ? Component.translatable("settlement.starrailexpress.unknown_role") : RoleUtils.getRoleName(id);
        int color = actor.fact.color() | 0xFF000000;
        pose.pushPose();
        pose.translate(x, y + (physical ? .9 : (mvp ? 2.62 : 2.18)), z);
        pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
        pose.scale(.011f, -.011f, .011f);
        drawLabel(pose, buffer, name, -5, mvp ? 0xFFD4AF37 : 0xFFFFF4DC);
        drawLabel(pose, buffer, role, 7, color);
        if (mvp) drawLabel(pose, buffer, Component.translatable("settlement.starrailexpress.mvp"), -17, 0xFFD4AF37);
        pose.popPose();
        if (actor.blackness > .25f) {
            pose.pushPose(); pose.translate(x, y + (physical ? .95 : 1.55), z - .30);
            pose.mulPose(mc.gameRenderer.getMainCamera().rotation()); pose.scale(.035f, -.035f, .035f);
            drawLabel(pose, buffer, Component.literal("×"), 0, 0xFFE06B65);
            pose.popPose();
        }
    }

    private static void drawLabel(PoseStack pose, MultiBufferSource buffer, Component text, int y, int color) {
        Minecraft mc = Minecraft.getInstance();
        var lines = mc.font.split(text, 130);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            mc.font.drawInBatch(lines.get(i), -mc.font.width(lines.get(i)) * .5f, y + i * 10, color, false,
                    pose.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    private static void line(PoseStack pose, VertexConsumer buffer, double ax, double ay, double az,
                             double bx, double by, double bz, float r, float g, float b, float alpha) {
        float dx = (float) (bx - ax), dy = (float) (by - ay), dz = (float) (bz - az);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .0001f) return;
        buffer.addVertex(pose.last(), (float) ax, (float) ay, (float) az).setColor(r, g, b, alpha)
                .setNormal(pose.last(), dx / length, dy / length, dz / length);
        buffer.addVertex(pose.last(), (float) bx, (float) by, (float) bz).setColor(r, g, b, alpha)
                .setNormal(pose.last(), dx / length, dy / length, dz / length);
    }
}
