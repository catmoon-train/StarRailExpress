package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapPreviewPayload;
import java.util.*;

/** 管理员私有本地线框；最多256近框、32近标签，30秒后自动清理。 */
public final class EchoMapPreviewClient {
    private static EchoMapPreviewPayload preview;
    private static int remaining;
    private static List<EchoMapPreviewPayload.Entry> visible=List.of();
    private static final MultiBufferSource.BufferSource BUFFER=MultiBufferSource.immediate(new ByteBufferBuilder(32768));
    private EchoMapPreviewClient() { }
    private static Vec3 center(EchoMapPreviewPayload.Entry e) { var b=e.box(); return new Vec3((b.minX()+b.maxX())/2,(b.minY()+b.maxY())/2,(b.minZ()+b.maxZ())/2); }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoMapPreviewPayload.ID,(payload,context) -> context.client().execute(() -> { preview=payload; remaining=600; visible=List.of(); }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(preview==null) return;
            if(client.level==null || client.player==null || !client.level.dimension().location().equals(preview.dimension()) || --remaining<=0
                    || SREGameWorldComponent.KEY.get(client.level).getGameStatus()!=SREGameWorldComponent.GameStatus.INACTIVE) { preview=null; visible=List.of(); return; }
            if(remaining%10==0 || visible.isEmpty()) {
                Vec3 eye=client.player.getEyePosition();
                visible=preview.entries().stream().filter(e -> center(e).distanceToSqr(eye)<=128*128)
                        .sorted(Comparator.comparingDouble(e -> center(e).distanceToSqr(eye))).limit(256).toList();
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var client=Minecraft.getInstance(); if(preview==null || client.level==null || context.matrixStack()==null) return;
            var pose=context.matrixStack(); Vec3 camera=context.camera().getPosition();
            pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
            var lines=BUFFER.getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
            for(var entry:visible) {
                var b=entry.box(); int c=entry.color();
                LevelRenderer.renderLineBox(pose,lines,new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),((c>>16)&255)/255F,((c>>8)&255)/255F,(c&255)/255F,0.85F);
            }
            BUFFER.endBatch(); int labels=0;
            for(var entry:visible) {
                var center=center(entry); if(center.distanceToSqr(camera)>48*48 || labels++>=32) continue;
                pose.pushPose(); pose.translate(center.x,entry.box().maxY()+0.25,center.z); pose.mulPose(client.getEntityRenderDispatcher().cameraOrientation()); pose.scale(-0.018F,-0.018F,0.018F);
                client.font.drawInBatch(entry.label(),-client.font.width(entry.label())/2F,0,0xFF000000|entry.color(),false,pose.last().pose(),BUFFER,Font.DisplayMode.SEE_THROUGH,0x80000000,15728880);
                pose.popPose();
            }
            BUFFER.endBatch(); pose.popPose();
        });
    }
}
