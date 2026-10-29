package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.content.EchoCardProxyItem;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import java.util.*;

/** 代理仅采集目标意图；发光、方向与到期均读取本人的服务端授权。 */
public final class EchoCardClient {
    private static Vec3 firstPoint;
    private static UUID firstInstance;
    private static long firstRevision;
    private static int firstSlot = -1;
    private static final MultiBufferSource.BufferSource LINES=MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private EchoCardClient() { }
    public static boolean selectingSecond() { return firstPoint!=null; }
    public static void initialize() {
        EchoCardProxyItem.clientUse=EchoCardClient::use;
        org.agmas.noellesroles.game.modes.echo.content.EchoResourceItem.clientUse=EchoCardClient::use;
        EchoSceneClient.initialize();
        EchoMapPreviewClient.initialize();
        EchoHud.initialize();
        EchoSoundClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data=EchoBagClient.data();
            var instance=heldInstance();
            if(data==null || instance==null || !instance.instanceId().equals(firstInstance) || client.screen!=null
                    || data.bag()==null || data.bag().revision()!=firstRevision || client.player.getInventory().selected!=firstSlot) { firstPoint=null; firstInstance=null; }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var client=Minecraft.getInstance(); var data=EchoBagClient.data();
            if(client.level==null || client.player==null || client.player.isSpectator() || data==null || !data.life().standing() || context.matrixStack()==null) return;
            var pose=context.matrixStack(); Vec3 camera=context.camera().getPosition();
            pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
            var buffer=LINES.getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
            if(firstPoint!=null) LevelRenderer.renderLineBox(pose,buffer,new AABB(firstPoint.add(-0.2,0,-0.2),firstPoint.add(0.2,0.4,0.2)),1F,0.8F,0.2F,0.9F);
            pose.popPose(); LINES.endBatch();
        });
    }
    /** 原版/其它来源发光保持不变，队友和追踪轮廓仅消费服务器给本人的授权。 */
    public static boolean shouldGlow(Entity target) {
        var client=Minecraft.getInstance(); var data=EchoBagClient.data();
        if (!(target instanceof Player) || client.player==null || client.level==null || target==client.player
                || target.level()!=client.level || !target.isAlive() || target.isSpectator()
                || client.player.isSpectator() || !client.player.isAlive() || data==null || !data.life().potentiallyEscapable()
                || !EchoInterceptorSkin.currentMatch(data.matchId())) return false;
        return org.agmas.noellesroles.game.modes.echo.visual.EchoVisualState.authorized(
                data.signals(),target.getUUID(),data.projectedTick(),data.life().standing());
    }

    /** 只按当前实际槽位从本人快照找实例，绝不读取展示栈组件中的资源身份。 */
    public static org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance heldInstance() {
        var client=Minecraft.getInstance(); var data=EchoBagClient.data();
        if(client.player==null || data==null || data.bag()==null) return null;
        int selected=client.player.getInventory().selected;
        int at=org.agmas.noellesroles.game.modes.echo.inventory.EchoHotbarSlots.resourceSlot(selected);
        if(at>=0) {
            var items=data.bag().slots().get(at).items();
            return items.isEmpty()?null:items.getFirst();
        }
        return selected==1 && data.selectedCard()!=null ? data.items().stream().filter(i -> i.instanceId().equals(data.selectedCard())).findFirst().orElse(null) : null;
    }
    private static void use() {
        var client=Minecraft.getInstance(); var data=EchoBagClient.data();
        if(client.player==null || client.level==null || client.screen!=null || data==null || !EchoHotbar.active()) return;
        if(EchoBagClient.waiting()) { feedback("echo.ui.waiting"); return; }
        var instance=heldInstance();
        if(instance==null) return;
        if(instance.kind()==org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance.Kind.PACK) {
            var draft=data.draft();
            boolean ongoing=draft!=null && (draft.state().equals("CHANNELING") || draft.state().equals("OFFERED"));
            if(draft!=null && draft.packId().equals(instance.instanceId()) && draft.state().equals("BOUND")
                    && data.projectedTick()<draft.readyAt()) {
                client.setScreen(new io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen(client.player));
                return; // 只显示已有取消冷却的倒计时，不重复提交必然失败的拆包请求。
            }
            // 快捷栏右键直接开始拆包；已有引导/候选只恢复原流程，不重复提交。
            if(ongoing || EchoBagClient.send(EchoInventoryPayload.Operation.BEGIN_PACK,instance.instanceId(),null,0))
                client.setScreen(new io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen(client.player));
            return;
        }
        if(!data.life().standing() || data.controlled()) { feedback("echo.error.downed"); return; }
        if(data.bag().locks().containsKey(instance.instanceId())) { feedback("echo.error.locked"); return; }
        var card=data.rules().card(instance.definitionId());
        if(!card.usableBy(data.faction())) { feedback("echo.error.wrong_faction"); return; }
        if(card.activation()==EchoCardDefinition.Activation.PASSIVE) { feedback("echo.ui.passive"); return; }
        if(card.target()==EchoCardDefinition.Target.SELF || card.target()==EchoCardDefinition.Target.DIRECTION) {
            firstPoint=null; firstInstance=null;
            EchoBagClient.send(EchoInventoryPayload.Operation.USE_CARD,instance.instanceId(),null,0);
            return;
        }
        double range=card.params().getOrDefault("range",12D);
        var hit=client.player.pick(range,0,false); Vec3 point=hit.getLocation(); UUID target=null;
        if(card.target()==EchoCardDefinition.Target.PLAYER) {
            Vec3 eye=client.player.getEyePosition(),end=eye.add(client.player.getLookAngle().scale(range));
            double nearest=hit.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(hit.getLocation());
            for(Player p:client.level.players()) if(p!=client.player && !p.isSpectator()) {
                var intersection=p.getBoundingBox().inflate(0.05).clip(eye,end);
                if(intersection.isPresent() && eye.distanceToSqr(intersection.get())<nearest) { nearest=eye.distanceToSqr(intersection.get()); target=p.getUUID(); }
            }
            if(target==null) { feedback("echo.error.no_target"); return; }
        } else if(hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK) {
            point=card.target()==EchoCardDefinition.Target.BEACON || card.target()==EchoCardDefinition.Target.CORE_GATE?Vec3.atCenterOf(block.getBlockPos()):hit.getLocation();
        } else { feedback("echo.error.no_target"); return; }
        if(firstPoint!=null && (!instance.instanceId().equals(firstInstance) || data.bag().revision()!=firstRevision || client.player.getInventory().selected!=firstSlot)) { firstPoint=null; firstInstance=null; }
        if(card.target()==EchoCardDefinition.Target.GROUND_PAIR && firstPoint==null) {
            firstPoint=point; firstInstance=instance.instanceId(); firstRevision=data.bag().revision(); firstSlot=client.player.getInventory().selected;
            feedback("echo.ui.second_point"); return;
        }
        Vec3 previous=firstPoint; firstPoint=null; firstInstance=null;
        EchoBagClient.send(EchoInventoryPayload.Operation.USE_CARD,instance.instanceId(),target,card.shortId().equals("forged_signal") && client.player.isShiftKeyDown()?1:0,previous==null?point:previous,previous==null?Vec3.ZERO:point);
    }
    private static void feedback(String key) {
        var player=Minecraft.getInstance().player;
        if(player!=null) player.displayClientMessage(Component.translatable(key),true);
    }
}
