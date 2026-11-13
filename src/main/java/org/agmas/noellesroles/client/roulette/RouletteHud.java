package org.agmas.noellesroles.client.roulette;

import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.game.minigame.roulette.*;

/** 屏幕下部仅保留对局核心信息，详细规则与道具说明通过桌面帮助查看。 */
final class RouletteHud {
    static final int TEXT=0xFFFFF4DC, GOLD=0xFFD4AF37, MUTED=0xFF9E8B6E, RED=0xFFE06B65, BLUE=0xFF5EB7D8;
    private RouletteHud() {}

    static void render(GuiGraphics g, DeltaTracker tracker) {
        var mc=Minecraft.getInstance();
        var state=RouletteClient.participant();
        if (state==null || mc.player==null || mc.level==null || mc.screen!=null || mc.options.hideGui
                || !(mc.player.getVehicle() instanceof io.wifi.starrailexpress.content.block.entity.SeatEntity)
                || mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(state.pos))>36
                || AdvancedCameraDirector.shouldHideHudForCamera()) return;
        RouletteView v=state.view;
        float partial=tracker.getGameTimeDeltaPartialTick(false);
        vignette(g,state,partial);
        RouletteAmmoHud.render(g,v);
        var self=mc.player.getUUID();
        var opponent=v.front().equals(self)?v.back():v.front();
        boolean myTurn=v.phase()==RoulettePhase.READY && v.operator().equals(self);
        var secret=RouletteClient.privateInfo(state);
        boolean hasStatus=v.saw() || !v.cuffed().equals(RouletteView.NONE);
        var box=RouletteHudLayout.bottom(g.guiWidth(),g.guiHeight(),(myTurn?1:0)+(secret==null?0:1)+(hasStatus?1:0));
        int x=box.x(), y=box.y(), w=box.width();
        if (w<100) return;
        g.fillGradient(x,y,x+w,y+box.height(),0xB01A1008,0xB00E0905);
        g.renderOutline(x,y,w,box.height(),0xAA8B6914);
        Component phase=v.phase()==RoulettePhase.READY
                ? Component.translatable(myTurn?"roulette.hud.your_turn":"roulette.hud.other_turn")
                : Component.translatable(v.phase().titleKey());
        fitted(g,phase,x+8,y+7,w-75,myTurn?GOLD:TEXT);
        if (v.phase()==RoulettePhase.READY) {
            Component time=Component.translatable("roulette.ui.seconds",state.seconds());
            g.drawString(mc.font,time,x+w-8-mc.font.width(time),y+7,state.seconds()<=5?RED:BLUE,false);
        }
        fitted(g,Component.translatable("roulette.hud.health",v.health(self),v.maxHealth(self),
                v.health(opponent),v.maxHealth(opponent)),x+8,y+20,w-16,TEXT);
        var status=Component.empty();
        if (v.saw()) status.append(Component.translatable("roulette.hud.saw"));
        if (!v.cuffed().equals(RouletteView.NONE)) {
            if (v.saw()) status.append(" · ");
            status.append(Component.translatable(v.cuffed().equals(self)?"roulette.hud.cuffed_self":"roulette.hud.cuffed_other"));
        }
        int row=y+33;
        if (hasStatus) { fitted(g,status,x+8,row,w-16,GOLD); row+=12; }
        if (myTurn) {
            var held=mc.player.getMainHandItem();
            if (held.isEmpty() && RouletteItems.kind(mc.player.getOffhandItem())!=null) held=mc.player.getOffhandItem();
            fitted(g,hint(state,zone(state),RouletteItems.kind(held),held.isEmpty()),x+8,row,w-16,MUTED);
            row+=12;
        }
        if (secret!=null) {
            Component text=secret.index()>0
                    ? Component.translatable(secret.key(),secret.index(),Component.translatable(secret.shell()==1?"roulette.ui.live":"roulette.ui.blank"))
                    : Component.translatable(secret.key());
            fitted(g,Component.translatable("roulette.hud.private",text),x+8,row,w-16,secret.shell()==1?RED:BLUE);
        }
    }

    static int zone(RouletteClient.State state) {
        var mc=Minecraft.getInstance();
        if (mc.level==null || mc.player==null || !state.view.operator().equals(mc.player.getUUID())
                || state.view.phase()!=RoulettePhase.READY || !(mc.hitResult instanceof BlockHitResult hit)) return -1;
        return mc.level.getBlockEntity(state.pos) instanceof DevilRouletteTableEntity table
                ? table.operation(mc.player.getUUID(),hit.getBlockPos()):-1;
    }

    private static Component hint(RouletteClient.State state,int zone,RouletteItem kind,boolean empty) {
        var mc=Minecraft.getInstance();
        RouletteView v=state.view;
        if (zone==0) return Component.translatable("roulette.hud.shoot_self");
        if (zone==2) return Component.translatable("roulette.hud.shoot_other");
        if (zone!=1) return Component.translatable("roulette.hud.aim");
        if (empty) return Component.translatable("roulette.hud.help");
        if (kind==null) return Component.translatable("roulette.hint.invalid_item");
        if (kind==RouletteItem.HANDSAW && v.saw()) return Component.translatable("roulette.hint.already_sawn");
        if (kind==RouletteItem.HANDCUFFS && !v.cuffed().equals(RouletteView.NONE)) return Component.translatable("roulette.hint.already_cuffed");
        if (kind==RouletteItem.CIGARETTE && v.health(mc.player.getUUID())>=v.maxHealth(mc.player.getUUID())) return Component.translatable("roulette.hint.full_health");
        if (kind==RouletteItem.TELEPHONE && v.remaining()<2) return Component.translatable("roulette.error.no_future");
        var opponent=v.front().equals(mc.player.getUUID())?v.back():v.front();
        if (kind==RouletteItem.ADRENALINE && v.items(opponent).isEmpty()) return Component.translatable("roulette.hint.no_choices");
        return Component.translatable("roulette.hud.use",Component.translatable(kind.titleKey()));
    }

    static void panel(GuiGraphics g,int x,int y,int w,int h) {
        g.fillGradient(x,y,x+w,y+h,0xD81A1008,0xD820140A);
        g.renderOutline(x,y,w,h,0xFF8B6914);
        g.fill(x+1,y+1,x+w-1,y+2,0x33FFE8C0);
    }

    static void fitted(GuiGraphics g,Component text,int x,int y,int width,int color) {
        var font=Minecraft.getInstance().font;
        float scale=Mth.clamp(width/(float)Math.max(1,font.width(text)),.1F,1F);
        g.pose().pushPose(); g.pose().translate(x,y,0); g.pose().scale(scale,scale,1);
        g.drawString(font,text,0,0,color,false); g.pose().popPose();
    }

    private static void vignette(GuiGraphics g,RouletteClient.State state,float partial) {
        var mc=Minecraft.getInstance();
        int hp=state.view.health(mc.player.getUUID());
        float low=hp==1&&state.view.phase()!=RoulettePhase.ENDING
                ? .035F+.02F*(float)Math.sin((mc.level.getGameTime()+partial)*.28):0;
        float strength=Mth.clamp(low+RouletteEffects.hurtPulse(partial),0,.38F);
        int alpha=(int)(strength*110), w=g.guiWidth(),h=g.guiHeight(),edge=Math.min(28,h/8);
        int color=alpha<<24|0x771B18;
        g.fillGradient(0,0,w,edge,color,0x00771B18);
        g.fillGradient(0,h-edge,w,h,0x00771B18,color);
        for (int i=0;i<edge;i++) {
            int c=(int)(alpha*(1-i/(float)edge))<<24|0x771B18;
            g.fill(i,edge,i+1,h-edge,c); g.fill(w-i-1,edge,w-i,h-edge,c);
        }
    }
}
