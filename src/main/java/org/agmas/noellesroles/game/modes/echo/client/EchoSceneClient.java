package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.event.client.OnRenderRoleName;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 假影直接绘制皮肤模型，不加入客户端世界实体表；公开幻象与私有轮廓分开绘制。 */
public final class EchoSceneClient {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new ByteBufferBuilder(8192));
    private static PlayerModel<Player> wideModel,slimModel;
    private static Object modelSet;
    private static UUID lastMatch;
    private static long lastParticles=-1;
    private static boolean held;
    private EchoSceneClient() { }
    public static void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var client=Minecraft.getInstance(); var data=EchoBagClient.data();
            if(client.level==null || client.player==null || client.player.isSpectator() || data==null || !data.life().potentiallyEscapable() || context.matrixStack()==null) return;
            if(wideModel==null || slimModel==null || modelSet!=client.getEntityModels()) {
                modelSet=client.getEntityModels(); wideModel=new PlayerModel<>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
                slimModel=new PlayerModel<>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM),true);
            }
            var pose=context.matrixStack(); var camera=context.camera().getPosition();
            pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
            for(var s:data.scenes()) if(s.active(data.projectedTick())) {
                Vec3 pos=EchoSceneService.vec(s.point());
                if(s.kind().decoy()) {
                    var owner=client.level.getPlayerByUUID(s.owner());
                    var skin=owner instanceof AbstractClientPlayer p?p.getSkin():DefaultPlayerSkin.get(s.owner());
                    var model=skin.model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM?slimModel:wideModel;
                    pose.pushPose(); pose.translate(pos.x,pos.y,pos.z); pose.mulPose(Axis.YP.rotationDegrees(180-s.yaw())); pose.scale(-1,-1,1); pose.translate(0,-1.501,0);
                    model.setAllVisible(true); model.crouching=false; model.riding=false; model.young=false; model.attackTime=0;
                    model.setupAnim(client.player,0,0,0,0,0);
                    long remain=s.end()-data.projectedTick();
                    float life=Math.max(0.15F,Math.min(1F,remain/20F));
                    int baseAlpha=s.kind()==EchoSceneState.Kind.IMAGE?0x66:0xDD;
                    int tint=(int)(baseAlpha*life)<<24 | (s.kind()==EchoSceneState.Kind.IMAGE?0x7ACFFF:0xFFFFFF);
                    model.renderToBuffer(pose,BUFFER.getBuffer(RenderType.entityTranslucent(skin.texture())),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,tint);
                    pose.popPose();
                } else {
                    boolean purple=s.kind().stall();
                    var lines=BUFFER.getBuffer(RenderType.lines());
                    float r=purple?0.8F:0.3F,g=purple?0.2F:1F,b=purple?0.95F:0.7F;
                    double width=0.3+0.12*Math.sin((data.projectedTick()-s.start())*0.15);
                    LevelRenderer.renderLineBox(pose,lines,new AABB(pos.add(-width,0,-width),pos.add(width,3,width)),r,g,b,0.8F);
                    LevelRenderer.renderLineBox(pose,lines,new AABB(pos.add(-0.7,0.03,-0.7),pos.add(0.7,0.07,0.7)),r,g,b,0.9F);
                }
            }
            pose.popPose(); BUFFER.endBatch();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data=EchoBagClient.data(); boolean use=client.options.keyUse.isDown();
            if(client.player==null || client.level==null || data==null || client.player.isSpectator() || !data.life().potentiallyEscapable()) { held=use; lastMatch=null; lastParticles=-1; return; }
            if(!Objects.equals(lastMatch,data.matchId())) { lastMatch=data.matchId(); lastParticles=-1; }
            if(client.screen==null && use && !held && client.player.getMainHandItem().isEmpty() && data.faction()==EchoFaction.INTERCEPTOR
                    && client.hitResult!=null && client.hitResult.getType()==HitResult.Type.MISS && !EchoBagClient.waiting()) {
                Vec3 eye=client.player.getEyePosition(),end=eye.add(client.player.getLookAngle().scale(2.75));
                if(data.scenes().stream().anyMatch(s -> s.kind().decoy() && s.active(data.projectedTick())
                        && new AABB(EchoSceneService.vec(s.point()).add(-0.3,0,-0.3),EchoSceneService.vec(s.point()).add(0.3,1.85,0.3)).clip(eye,end).isPresent()))
                    EchoBagClient.send(EchoInventoryPayload.Operation.DISMANTLE_SCENE,null,null,0);
            }
            held=use;
            if(data.projectedTick()==lastParticles || data.projectedTick()%5!=0) return;
            lastParticles=data.projectedTick();
            for(var s:data.scenes()) if(!s.kind().decoy() && s.active(data.projectedTick()) && client.player.distanceToSqr(EchoSceneService.vec(s.point()))<=32*32) {
                for(int i=0;i<4;i++) {
                    double angle=(data.projectedTick()-s.start())*0.1+i*Math.PI/2;
                    client.level.addParticle(s.kind().stall()?ParticleTypes.WITCH:ParticleTypes.END_ROD,s.point().x()+Math.cos(angle)*0.6,s.point().y()+0.2+i*0.55,s.point().z()+Math.sin(angle)*0.6,0,0.03,0);
                }
            }
        });
        OnRenderRoleName.RENDER_PLAYER_NAME.register((self,target,context,delta,font) -> {
            var data=EchoBagClient.data();
            if(!echoObserver(self,data) || target==null || target.isSpectator()) return TrueFalseAndCustomResult.pass();
            boolean masked=data.signals().stream().anyMatch(s -> (s.kind().equals("mask") || s.kind().equals("ally")) && target.getUUID().equals(s.target()) && data.projectedTick()<s.until());
            int color=masked?switch(data.faction()) { case CARRIER -> 0x68BA87; case INTERCEPTOR -> 0xDC6B65; case BROKER -> 0xD4AF37; }:0xE8DCC5;
            return TrueFalseAndCustomResult.custom(target.getDisplayName().copy().withStyle(style -> style.withColor(color)));
        });
        OnRenderRoleName.RenderPlayerNameInterface hide=(self,target,context,delta,font) -> echoObserver(self,EchoBagClient.data())?TrueFalseAndCustomResult.disallow():TrueFalseAndCustomResult.pass();
        OnRenderRoleName.RENDER_PLAYER_ROLE.register(hide);
        OnRenderRoleName.RENDER_PLAYER_COHORT.register(hide);
        OnRenderRoleName.RENDER_PLAYER_MODIFIER.register(hide);
    }
    private static boolean echoObserver(Player self,EchoRoleData data) { return self!=null && data!=null && data.matchId()!=null && data.phase()!=EchoPhase.FINISHED && !self.isSpectator() && data.life().potentiallyEscapable(); }
}
