package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.game.modes.echo.objective.EchoPublicPayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoPhase;
import java.util.*;

/** 最多三公开信标的本地线柱/高亮与低鸣，不创建世界实体或追加粒子网络包。 */
public final class EchoObjectiveClient {
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private static final Map<String,Long> hums=new HashMap<>();
    private static UUID lastMatch;
    private static long lastParticles=-1;
    private EchoObjectiveClient() { }
    private static EchoPublicPayload live() {
        var client=Minecraft.getInstance(); var view=EchoHud.view();
        return client.player!=null && client.level!=null && view!=null && EchoHud.age()<=20
                && view.dimension().equals(client.level.dimension().location())?view:null;
    }
    public static void initialize() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var view=live(); if(view==null || context.matrixStack()==null) return;
            var client=Minecraft.getInstance(); var state=view.state(); long now=state.displayTick(EchoHud.age());
            var pose=context.matrixStack(); var camera=context.camera().getPosition();
            pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
            var lines=BUFFER.getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
            for(var b:state.beacons()) if(b.position()!=null) {
                var p=b.position(); var pos=new Vec3(p.x(),p.y(),p.z());
                boolean highlighted=b.highlighted(now),charging=state.phase()==EchoPhase.OPERATIONS && b.charging();
                if(!highlighted && (!charging || client.player.distanceToSqr(pos)>32*32)) continue;
                float glow=charging?0.75F+0.25F*EchoAnim.breathe(1600):0.85F;
                float r=(highlighted?0.3F:b.working(now)?0.35F:0.8F)*glow,g=(highlighted?1F:b.working(now)?0.85F:0.2F)*glow,blue=(highlighted?0.7F:0.95F)*glow;
                double radius=highlighted?0.7:0.14,height=highlighted?3:5;
                LevelRenderer.renderLineBox(pose,lines,new AABB(pos.add(-radius,0.03,-radius),pos.add(radius,height,radius)),r,g,blue,0.8F);
                if(charging) LevelRenderer.renderLineBox(pose,lines,new AABB(pos.add(-0.5,0.05,-0.5),pos.add(0.5,0.12,0.5)),r,g,blue,0.6F);
            }
            pose.popPose(); BUFFER.endBatch();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var view=live();
            if(view==null) { lastMatch=null; lastParticles=-1; hums.clear(); return; }
            if(!view.match().equals(lastMatch)) { lastMatch=view.match(); lastParticles=-1; hums.clear(); }
            var state=view.state(); long now=state.displayTick(EchoHud.age());
            if(state.paused() || state.phase()!=EchoPhase.OPERATIONS) return;
            boolean particles=now!=lastParticles && now%5==0;
            if(particles) lastParticles=now;
            for(var b:state.beacons()) if(b.position()!=null && b.working(now)) {
                var p=b.position(); var pos=new Vec3(p.x(),p.y(),p.z());
                if(client.player.distanceToSqr(pos)>16*16) continue;
                long bucket=now/40;
                if(hums.getOrDefault(b.id(),-1L)!=bucket) {
                    hums.put(b.id(),bucket);
                    client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_RESONATE,SoundSource.BLOCKS,0.15F,0.65F,RandomSource.create(),p.x(),p.y(),p.z()));
                }
                if(particles) for(int n=0;n<3;n++) client.level.addParticle(ParticleTypes.END_ROD,p.x(),p.y()+0.5+n*1.4,p.z(),0,0.025,0);
            }
        });
    }
}
