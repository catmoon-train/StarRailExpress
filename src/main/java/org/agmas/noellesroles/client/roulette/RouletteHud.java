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

/** 仅对参与者显示操作信息；旁观者只能看到桌面公开信息。 */
final class RouletteHud {
    static final int TEXT=0xFFFFF4DC, GOLD=0xFFD4AF37, MUTED=0xFF9E8B6E, RED=0xFFE06B65, BLUE=0xFF5EB7D8;
    private RouletteHud() {}

    static void render(GuiGraphics g, DeltaTracker tracker) {
        var mc=Minecraft.getInstance();
        var state=RouletteClient.participant();
        if (state==null || mc.player==null || mc.level==null || mc.options.hideGui || AdvancedCameraDirector.shouldHideHudForCamera()) return;
        RouletteView v=state.view;
        float partial=tracker.getGameTimeDeltaPartialTick(false);
        vignette(g,state,partial);
        int w=Math.min(400,g.guiWidth()-16), x=(g.guiWidth()-w)/2, y=8;
        int zone=zone(state);
        var held=mc.player.getMainHandItem();
        if (held.isEmpty() && RouletteItems.kind(mc.player.getOffhandItem())!=null) held=mc.player.getOffhandItem();
        RouletteItem kind=RouletteItems.kind(held);
        Component hint=hint(state,zone,kind,held.isEmpty());
        var reason=mc.font.split(Component.translatable(v.message()),w-20);
        var hints=mc.font.split(hint,w-20);
        var secret=RouletteClient.privateInfo(state);
        Component privateText=secret==null?Component.empty():secret.index()>0
                ? Component.translatable(secret.key(),secret.index(),Component.translatable(secret.shell()==1?"roulette.ui.live":"roulette.ui.blank"))
                : Component.translatable(secret.key());
        var privateLines=mc.font.split(privateText,w-20);
        var status=Component.empty();
        if (v.saw()) status.append(Component.translatable(RouletteItem.HANDSAW.titleKey())).append(" ×2");
        if (!v.cuffed().equals(RouletteView.NONE)) {
            if (v.saw()) status.append("  |  ");
            status.append(Component.translatable(RouletteItem.HANDCUFFS.titleKey())).append(": ").append(v.name(v.cuffed()));
        }
        int statusHeight=status.getString().isEmpty()?0:11;
        int h=69+statusHeight+reason.size()*11+hints.size()*11+(secret==null?0:14+privateLines.size()*11);
        panel(g,x,y,w,h);
        Component phase=Component.translatable(v.phase().titleKey());
        var actor=(v.phase()==RoulettePhase.SHOOT || v.phase()==RoulettePhase.ITEM || v.phase()==RoulettePhase.ADRENALINE)?v.actor():v.operator();
        fitted(g,Component.translatable("roulette.ui.actor",actor.equals(RouletteView.NONE)
                ? Component.translatable("roulette.ui.empty_seat"):Component.literal(v.name(actor))),x+10,y+8,w-mc.font.width(phase)-30,GOLD);
        g.drawString(mc.font,phase,x+w-10-mc.font.width(phase),y+8,TEXT,false);
        var self=mc.player.getUUID();
        var opponent=v.front().equals(self)?v.back():v.front();
        Component hp=Component.translatable("roulette.ui.health_max",v.health(self),v.maxHealth(self),
                v.health(opponent),v.maxHealth(opponent));
        g.drawString(mc.font,hp,x+10,y+23,TEXT,false);
        Component time=Component.translatable("roulette.ui.seconds",state.seconds());
        if (v.phase()==RoulettePhase.READY) g.drawString(mc.font,time,x+w-10-mc.font.width(time),y+23,
                state.seconds()<=5 && mc.level.getGameTime()%10<5?RED:BLUE,false);
        g.drawString(mc.font,Component.translatable("roulette.ui.loaded",v.live(),v.blanks(),v.remaining()),x+10,y+37,MUTED,false);
        if (statusHeight>0) fitted(g,status,x+10,y+48,w-20,GOLD);
        int controlsY=y+50+statusHeight;
        int cell=(w-20)/3;
        for (int i=0;i<3;i++) {
            int left=x+10+i*cell;
            g.fill(left,controlsY,left+cell-2,controlsY+14,zone==i?0x554D3B18:0x55120A04);
            if (zone==i) g.renderOutline(left,controlsY,cell-2,14,GOLD);
            Component label=Component.translatable(i==0?"roulette.ui.self":i==1?"roulette.ui.item_help":"roulette.ui.opponent");
            fitted(g,label,left+3,controlsY+3,cell-8,zone==i?GOLD:MUTED);
        }
        int row=controlsY+19;
        for (var line:reason) { g.drawString(mc.font,line,x+10,row,TEXT,false); row+=11; }
        for (var line:hints) { g.drawString(mc.font,line,x+10,row,zone==1&&kind!=null?GOLD:MUTED,false); row+=11; }
        if (secret!=null) {
            g.fill(x+9,row+2,x+w-9,row+3,0x448B6914); row+=7;
            g.drawString(mc.font,Component.translatable("roulette.ui.private"),x+10,row,BLUE,false); row+=11;
            for (var line:privateLines) { g.drawString(mc.font,line,x+10,row,secret.shell()==1?RED:BLUE,false); row+=11; }
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
        if (v.phase()==RoulettePhase.WAITING) return Component.translatable("roulette.hint.sit");
        if (v.phase()!=RoulettePhase.READY) return Component.translatable("roulette.hint.wait_animation");
        if (!v.operator().equals(mc.player.getUUID())) return Component.translatable("roulette.hint.wait_turn");
        if (zone==0) return Component.translatable("roulette.hint.aim_self");
        if (zone==2) return Component.translatable("roulette.hint.aim_opponent");
        if (zone!=1) return Component.translatable("roulette.hint.aim_zone");
        if (empty) return Component.translatable("roulette.hint.help");
        if (kind==null) return Component.translatable("roulette.hint.invalid_item");
        if (kind==RouletteItem.HANDSAW && v.saw()) return Component.translatable("roulette.hint.already_sawn");
        if (kind==RouletteItem.HANDCUFFS && !v.cuffed().equals(RouletteView.NONE)) return Component.translatable("roulette.hint.already_cuffed");
        if (kind==RouletteItem.CIGARETTE && v.health(mc.player.getUUID())>=v.maxHealth(mc.player.getUUID())) return Component.translatable("roulette.hint.full_health");
        if (kind==RouletteItem.TELEPHONE && v.remaining()<2) return Component.translatable("roulette.error.no_future");
        var opponent=v.front().equals(mc.player.getUUID())?v.back():v.front();
        if (kind==RouletteItem.ADRENALINE && v.items(opponent).isEmpty()) return Component.translatable("roulette.hint.no_choices");
        return Component.translatable(kind.descriptionKey());
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
        float low=hp>0&&hp<=2&&state.view.phase()!=RoulettePhase.ENDING
                ? .055F+.035F*(float)Math.sin((mc.level.getGameTime()+partial)*.28):0;
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
