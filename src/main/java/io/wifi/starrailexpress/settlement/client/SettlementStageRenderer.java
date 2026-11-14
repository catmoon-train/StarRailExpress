package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.util.ClientSkinCache;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** 不创建假玩家、不调用实体渲染器、setupAnim 或职业姿态钩子；仅直接绘制独立 PlayerModel。 */
public final class SettlementStageRenderer {
    private static PlayerModel<Player> wide, slim;
    private static Object models;
    record Label(SettlementClient.ActorState actor, SettlementPresentation.Rect head, double x, double y, boolean active) { }
    static final List<Label> labels = new ArrayList<>();
    private static Matrix4f viewProjection;
    static int labelsAt = -1000, labelsWidth, labelsHeight;
    static void clearLabels() { labels.clear(); labelsAt = -1000; }
    private SettlementStageRenderer() { }

    static void invalidate() { wide = slim = null; models = null; }

    private static void ensureModels(Minecraft mc) {
        if (wide==null || models!=mc.getEntityModels()) {
            models=mc.getEntityModels();
            wide=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
            slim=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM),true);
        }
    }

    /** 海报只绘制本局 MVP，既不投影舞台标签，也不调用职业实体渲染钩子。 */
    static void renderPortrait(PoseStack pose,MultiBufferSource buffer,SettlementClient.ActorState actor,double time) {
        Minecraft mc=Minecraft.getInstance();ensureModels(mc);
        var info=ClientSkinCache.getCachedPlayerInfo(actor.fact.id());
        if(info==null && mc.getConnection()!=null) info=mc.getConnection().getPlayerInfo(actor.fact.id());
        PlayerSkin skin=info==null?DefaultPlayerSkin.get(actor.fact.id()):info.getSkin();
        PlayerModel<Player> model=skin.model()==PlayerSkin.Model.SLIM?slim:wide;
        setPose(model,actor,time,0,true);
        float entrance=(float)SettlementPresentation.easeOut((time-206)/15);
        float salute=(float)(SettlementPresentation.smooth((time-211)/8)*(1-SettlementPresentation.smooth((time-246)/15)));
        model.body.yRot=-.20f*entrance;model.body.xRot=.025f*salute;
        model.head.yRot=.16f*entrance;model.head.xRot=-.04f+.08f*salute;
        model.rightArm.xRot=-.12f-1.87f*salute;model.rightArm.zRot=.10f+.23f*salute;
        model.rightArm.yRot=-.13f*salute;
        model.leftArm.xRot=-.35f*entrance;model.leftArm.zRot=-.25f*entrance;
        model.leftLeg.xRot=.025f;model.rightLeg.xRot=-.025f;
        model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float)(18-30*SettlementPresentation.easeOut((time-206)/18))));
        pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        renderActor(pose,buffer,model,actor,skin,0xFFFFFFFF,true,0,false);
        pose.popPose();
    }

    static void render(WorldRenderContext context) {
        if (!SettlementClient.isPlaying() || SettlementClient.snapshot() == null || context.matrixStack() == null) return;
        Minecraft mc = Minecraft.getInstance();
        ensureModels(mc);
        var snapshot = SettlementClient.snapshot();
        // 皮肤/物品渲染可能重入客户端任务并清空结算；本帧不能遍历可变名单。
        var frameActors = List.copyOf(SettlementClient.actors);
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        double time = SettlementClient.tick() + partial;
        var frame = SettlementClient.presentation(partial);
        labels.clear(); labelsAt = SettlementClient.tick();
        labelsWidth=mc.getWindow().getGuiScaledWidth(); labelsHeight=mc.getWindow().getGuiScaledHeight();
        // 与实际顶点着色器使用相同的投影/视图链，不用固定站高猜测名签位置。
        viewProjection=new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Vec3 camera = context.camera().getPosition();
        pose.pushPose();
        pose.translate(snapshot.x() + .5 - camera.x, snapshot.y() + 1 - camera.y, snapshot.z() + .5 - camera.z);
        // 皮肤布景：本体只按 ID 查注册表，具体画什么由皮肤实现决定。
        SettlementSkin settlementSkin = SettlementSkins.byId(snapshot.mvpSkin());
        if (settlementSkin == null) {
            SettlementBackdropRenderer.render(pose,buffer,frame);
            buffer.endBatch(SettlementPaperRenderType.PAPER);
        }
        if (settlementSkin != null) {
            try {
                settlementSkin.renderBackdrop(pose, buffer, SettlementPresentation.stageTime(time));
            } catch (RuntimeException error) {
                io.wifi.starrailexpress.SRE.LOGGER.warn("结算皮肤布景回调失败: {}", snapshot.mvpSkin(), error);
            }
        }
        // 快照遍历：循环体内会调用外部皮肤回调，它们可能在遍历期间触发 clear()/arrange() 重入式修改列表，
        // 直接遍历 SettlementClient.actors 会抛 ConcurrentModificationException。拷贝一份可避免崩溃。
        for (var actor : frameActors) {
            if (!SettlementClient.isPlaying() || SettlementClient.snapshot() != snapshot) break;
            var info = ClientSkinCache.getCachedPlayerInfo(actor.fact.id());
            if (info == null && mc.getConnection() != null) info = mc.getConnection().getPlayerInfo(actor.fact.id());
            // 败方只以落下的尸体出现；不在舞台上先坐成一排，也不先升到天上。
            PlayerSkin skin = info == null ? DefaultPlayerSkin.get(actor.fact.id()) : info.getSkin();
            if (!SettlementClient.isPlaying() || SettlementClient.snapshot() != snapshot) break;
            PlayerModel<Player> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
            boolean physical = actor.body.active;
            if (!actor.fact.winner() && !physical) continue;
            double x = actor.x, y = actor.y, z = actor.z;
            if (physical) {
                var middle = actor.body.nodes[1];
                x = middle.x(partial); y = middle.y(partial); z = middle.z(partial);
            } else {
                y += SettlementPresentation.winnerLift(time, actor.order);
            }
            if (settlementSkin == null) SettlementBackdropRenderer.shadow(pose,buffer,x,z,physical?Math.max(0,y-.7):y,physical);
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
                pose.mulPose(Axis.ZP.rotationDegrees((float)(Math.sin(time*.055+actor.order)*1.4
                        *(1-SettlementClient.smooth((time-SettlementPresentation.ENTRANCE_END)/18)))));
            }
            // 此模型坐标朝 -Z，恰好面向舞台正面相机。
            pose.scale(-1, -1, 1); pose.translate(0, -1.501, 0);
            setPose(model, actor, time, action, mvp);
            float dark=actor.blackness;
            boolean focus=mvp || Objects.equals(actor.fact.id(),frame.primary()) || action>0;
            float brightness=focus?1f:(float)(1-frame.detail()*.16);
            int r=(int)((255-38*dark)*brightness),g=(int)((255-41*dark)*brightness),b=(int)((255-45*dark)*brightness);
            int tint=0xFF000000|r<<16|g<<8|b;
            renderActor(pose, buffer, model, actor, skin, tint, mvp, partial, true);
            pose.popPose();

            if(actor.fact.winner() && time<SettlementPresentation.ENTRANCE_END+18) {
                float alpha=(float)(1-SettlementClient.smooth((time-SettlementPresentation.ENTRANCE_END-2)/16));
                VertexConsumer lines = buffer.getBuffer(RenderType.lines());
                line(pose, lines, x - .22, y + 1.45, z, x - .4, 12.8, z, .8f, .72f, .52f, alpha);
                line(pose, lines, x + .22, y + 1.45, z, x + .4, 12.8, z, .8f, .72f, .52f, alpha);
            }
            // 皮肤专属的获胜者特效（本体不区分是哪款皮肤）
            if (settlementSkin != null && actor.fact.winner()) {
                try {
                    settlementSkin.renderWinnerEffect(pose, buffer, x, y, z, SettlementPresentation.stageTime(time), actor.order);
                } catch (RuntimeException error) {
                    io.wifi.starrailexpress.SRE.LOGGER.warn("结算皮肤获胜者特效回调失败: {}",
                            snapshot.mvpSkin(), error);
                }
            }
        }
        pose.popPose();
        buffer.endBatch();
    }

    /** 先画双腿，再让整个上半身绕腰部联动；不能只旋转 body 这个独立方块。 */
    private static void renderActor(PoseStack pose, MultiBufferSource buffer, PlayerModel<Player> model,
                                    SettlementClient.ActorState actor, PlayerSkin skin, int tint,
                                    boolean mvp, float partial, boolean stage) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(skin.texture()));
        for (var part : java.util.List.of(model.leftLeg, model.rightLeg, model.leftPants, model.rightPants))
            part.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        pose.pushPose();
        SettlementRig.upperBody(pose, model.body.xRot, model.body.yRot, model.body.zRot);
        // 躯干旋转已移到父级，清除本地旋转，外套不能再重复弯一次。
        model.body.xRot = model.body.yRot = model.body.zRot = 0;
        model.jacket.copyFrom(model.body);
        for (var part : java.util.List.of(model.body, model.jacket, model.head, model.hat,
                model.leftArm, model.rightArm, model.leftSleeve, model.rightSleeve))
            part.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
        if(stage) captureLabel(pose,model,actor,mvp);
        if(mvp) SettlementCrown.render(pose,buffer,model.head);
        renderHeldItem(pose,buffer,model,actor,partial,stage);
        pose.popPose();
    }

    /** 默认握持职业/商店道具；舞台事件动作期间优先展示该事件物品。 */
    private static void renderHeldItem(PoseStack pose, MultiBufferSource buffer, PlayerModel<Player> model,
                                       SettlementClient.ActorState actor, float partial, boolean stage) {
        var stack = actor.heldItem;
        var event = actor.actionEvent;
        double action = Math.max(0, 1 - (SettlementClient.tick() + partial - actor.actionAt) / 22);
        if (stage && action > 0 && event != null && event.item() != null
                && !event.item().isBlank() && actor.fact.id().equals(event.source())) {
            ResourceLocation id = ResourceLocation.tryParse(event.item());
            var item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null && item != net.minecraft.world.item.Items.AIR)
                stack = new net.minecraft.world.item.ItemStack(item);
        }
        if (stack.isEmpty()) return;

        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        // ModelPart 已转换到方块单位，不能再把像素坐标和十倍缩放直接用于物品。
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-1 / 16f, .125, -.625);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,
                net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffer, Minecraft.getInstance().level,
                actor.fact.id().hashCode());
        pose.popPose();
    }

    private static void setPose(PlayerModel<Player> model, SettlementClient.ActorState actor,
                                double time, double action, boolean mvp) {
        for (var part : java.util.List.of(model.head, model.hat, model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg, model.jacket, model.leftSleeve, model.rightSleeve,
                model.leftPants, model.rightPants)) part.resetPose();
        model.setAllVisible(true); model.young = false; model.crouching = false; model.riding = false;
        model.attackTime = 0; model.swimAmount = 0;
        double phase = actor.order * .67;
        double celebrationAt=SettlementPresentation.ENTRANCE_END+6;
        float ceremony=(float)SettlementClient.smooth((time-celebrationAt-actor.order%5)/16);
        float wave=(float)Math.sin((time-celebrationAt)*.14+phase);
        float celebration = (float) (1 - SettlementClient.smooth((SettlementPresentation.stageTime(time) - 205) / 65));
        model.head.xRot = -.035f; model.head.yRot = (float) Math.sin(time * .025 + phase) * .05f;
        model.leftArm.zRot = -.08f; model.rightArm.zRot = .08f;
        if (!actor.fact.winner()) {
            // 尸体四肢缓慢停摆；不再有“坐在舞台上”的待机姿势。
            float tumble = (float) Math.sin(time * .14 + actor.order) * .16f
                    * (float) (1 - SettlementClient.smooth((SettlementPresentation.stageTime(time) - 185) / 65));
            model.head.xRot = .18f;
            model.leftArm.xRot = -.18f; model.rightArm.xRot = .15f;
            model.leftArm.zRot = -.35f + tumble; model.rightArm.zRot = .35f - tumble;
            model.leftLeg.xRot = .18f + tumble; model.rightLeg.xRot = -.18f - tumble;
        } else if (actor.fact.killer()) {
            model.head.xRot += .04f * ceremony;
            model.body.yRot = -.08f * ceremony;
            model.leftArm.xRot = -.18f * ceremony; model.rightArm.xRot = -.26f * ceremony;
            model.leftArm.yRot = .08f * ceremony; model.rightArm.yRot = -.08f * ceremony;
            model.leftArm.zRot = -.10f * ceremony; model.rightArm.zRot = .12f * ceremony;
        } else if (neutralRole(actor)) {
            float bow=(float)Math.sin(SettlementClient.smooth((time-celebrationAt-actor.order%4*2)/45)*Math.PI)*ceremony;
            model.body.xRot = .42f * bow; model.head.xRot = .08f * bow;
            model.leftArm.xRot = model.rightArm.xRot = -.18f * bow;
        } else {
            model.rightArm.xRot = (-.12f - 2.05f * celebration) * ceremony;
            model.rightArm.zRot = (.12f + (.12f + wave * .12f) * celebration) * ceremony;
            model.head.zRot = (float) Math.sin(phase) * .035f * ceremony;
            if (mvp) {
                model.leftArm.xRot = -.48f * ceremony; model.leftArm.zRot = -.2f * ceremony;
                model.body.yRot = -.08f * ceremony;
            }
        }
        if (actor.fact.winner() && action>0 && actor.actionEvent!=null) {
            var event=actor.actionEvent;
            boolean source=actor.fact.id().equals(event.source());
            var semantic=SettlementPresentation.action(event.type());
            var motion=SettlementPresentation.motion(semantic,time-actor.actionAt);
            float gesture=(float)motion.hit(),windup=(float)motion.windup();
            if (source) {
                var target=SettlementClient.byId.get(event.target());
                float direction=target==null?0:(float)SettlementPresentation.clamp((target.x-actor.x)*.18,-.5,.5);
                model.head.yRot+=direction*gesture;
                switch (semantic) {
                    case PURCHASE -> { model.head.xRot+=.20f*gesture; model.rightArm.xRot=-.95f*gesture; model.rightArm.yRot=-.18f*gesture; }
                    case INTERACT -> { model.head.xRot+=.10f*gesture; model.rightArm.xRot=-.55f*gesture; }
                    case STRIKE -> {
                        model.rightArm.xRot=.55f*windup-1.85f*gesture;
                        model.body.yRot-=.16f*windup;model.body.yRot+=(.28f+direction*.35f)*gesture;
                        model.head.xRot-=.04f*windup;model.leftArm.xRot=-.35f*gesture;
                    }
                    case THROW -> {
                        model.rightArm.xRot=-2.45f*windup-1.45f*gesture;model.rightArm.zRot=.28f*(windup+gesture);
                        model.body.yRot-=.20f*windup;model.body.yRot+=.26f*gesture;
                    }
                    case CAST -> {
                        model.rightArm.xRot=-.6f*windup-1.25f*gesture;model.leftArm.xRot=-.65f*gesture;
                        model.rightArm.yRot=-.25f*gesture;model.body.xRot=.04f*gesture;
                    }
                    case TRANSFORM -> { model.body.yRot+=.28f*gesture; model.head.xRot-=.12f*gesture; }
                    case STILL -> { }
                }
            } else if (SettlementPresentation.category(event.type())==SettlementPresentation.Category.CONFLICT) {
                model.body.xRot-=.09f*gesture; model.head.xRot+=.15f*gesture;
                model.leftArm.zRot-=.12f*gesture; model.rightArm.zRot+=.12f*gesture;
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

    /** 从真实头部骨骼取八角包围盒，名签绘制留给最终 GUI 层统一避让。 */
    private static void captureLabel(PoseStack pose,PlayerModel<Player> model,SettlementClient.ActorState actor,boolean mvp) {
        pose.pushPose(); model.head.translateAndRotate(pose);
        Matrix4f transform=new Matrix4f(viewProjection).mul(pose.last().pose());
        pose.popPose();
        double x0=Double.POSITIVE_INFINITY,y0=Double.POSITIVE_INFINITY,x1=Double.NEGATIVE_INFINITY,y1=Double.NEGATIVE_INFINITY;
        for(int i=0;i<8;i++) {
            Vector4f point=new Vector4f((i&1)==0?-.32f:.32f,(i&2)==0?(mvp?-.72f:-.55f):.03f,(i&4)==0?-.32f:.32f,1).mul(transform);
            if(point.w<=.01f) return;
            double x=(point.x/point.w*.5+.5)*labelsWidth,y=(.5-point.y/point.w*.5)*labelsHeight;
            x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);
        }
        if(x1<0||x0>labelsWidth||y1<0||y0>labelsHeight) return;
        boolean active=actor.actionEvent!=null && SettlementClient.tick()-actor.actionAt<22;
        labels.add(new Label(actor,new SettlementPresentation.Rect(x0,y0,x1-x0,y1-y0),
                (x0+x1)/2,y1+Math.max(4,(y1-y0)*.3),active));
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
