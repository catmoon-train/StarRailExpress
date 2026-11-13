package org.agmas.noellesroles.client.roulette;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.minigame.roulette.*;

import java.util.*;

import static org.agmas.noellesroles.client.roulette.RouletteHud.*;

/** 无暂停的鼠标界面；取消不发包，选择只发送服务端给定的槽位和一次性令牌。 */
public final class RouletteScreen extends Screen {
    private final BlockPos pos;
    private final UUID session, token;
    private final List<Choice> choices;
    private final float[] hover;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int x,y,w,h,top,bottom,maxScroll;
    private boolean sent;
    private record Choice(int slot,RouletteItem kind,int count,String item) {}
    private record Row(int index,int top,int height) {}

    private RouletteScreen(BlockPos pos,UUID session,UUID token,List<Choice> choices) {
        super(Component.translatable(token==null?"roulette.ui.help_title":"roulette.ui.select_title"));
        this.pos=pos.immutable(); this.session=session; this.token=token; this.choices=List.copyOf(choices);
        hover=new float[token==null?9:choices.size()];
    }

    static RouletteScreen help(BlockPos pos,UUID session) { return new RouletteScreen(pos,session,null,List.of()); }

    static RouletteScreen selection(BlockPos pos,UUID session,CompoundTag data) {
        List<Choice> choices=new ArrayList<>();
        Set<Integer> seen=new HashSet<>();
        var list=data.getList("Choices",Tag.TAG_COMPOUND);
        for (int i=0;i<Math.min(46,list.size());i++) {
            CompoundTag entry=list.getCompound(i);
            int slot=entry.getInt("Slot"), count=entry.getInt("Count");
            RouletteItem kind=RouletteItem.byOrdinal(entry.getInt("Kind"));
            if (kind!=null && slot>=0 && slot<256 && count>0 && seen.add(slot))
                choices.add(new Choice(slot,kind,Math.min(64,count),entry.getString("Item")));
        }
        return new RouletteScreen(pos,session,data.getUUID("Token"),choices);
    }

    public BlockPos pos() { return pos; }
    public boolean matches(BlockPos p,UUID id) { return pos.equals(p) && session.equals(id); }
    @Override public boolean isPauseScreen() { return false; }

    public boolean valid() {
        var state=RouletteClient.get(pos);
        var mc=Minecraft.getInstance();
        return state!=null && mc.level!=null && mc.player!=null && session.equals(state.view.session())
                && (token==null || state.view.phase()==RoulettePhase.READY && state.view.operator().equals(mc.player.getUUID()));
    }

    @Override public void tick() { if (!valid()) onClose(); }

    private void layout() {
        w=Math.min(700,Math.max(100,(int)(width*.9F))); h=Math.min(360,height-16);
        x=(width-w)/2; y=(height-h)/2; bottom=y+h-34;
        int header=font.split(Component.translatable(token==null?"roulette.ui.rules":"roulette.ui.select_hint"),w-24).size();
        top=y+49+header*11;
        rows.clear(); int rowY=top;
        int size=token==null?RouletteItem.values().length:choices.size();
        for (int i=0;i<size;i++) {
            RouletteItem kind=token==null?RouletteItem.values()[i]:choices.get(i).kind();
            int rowHeight=Math.max(42,25+font.split(Component.translatable(kind.descriptionKey()),Math.max(30,w-64)).size()*11);
            rows.add(new Row(i,rowY,rowHeight)); rowY+=rowHeight+4;
        }
        maxScroll=Math.max(0,rowY-top-(bottom-top)); scroll=Mth.clamp(scroll,0,maxScroll);
    }

    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial) {
        layout(); g.fillGradient(0,0,width,height,0xA018120A,0xA0061018); panel(g,x,y,w,h);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        super.render(g,mouseX,mouseY,partial);
        fitted(g,title,x+12,y+10,w-24,GOLD);
        var state=RouletteClient.get(pos);
        Component subtitle=token==null?Component.translatable("roulette.ui.rules"):Component.translatable("roulette.ui.select_hint");
        int headerY=y+25;
        for (var line:font.split(subtitle,w-24)) { g.drawString(font,line,x+12,headerY,MUTED,false); headerY+=11; }
        if (state!=null && state.view.phase()==RoulettePhase.READY) {
            Component time=Component.translatable("roulette.ui.seconds",state.seconds());
            g.drawString(font,time,x+w-12-font.width(time),top-14,state.seconds()<=5?RED:BLUE,false);
        }
        g.enableScissor(x+7,top,x+w-7,bottom);
        for (Row row:rows) {
            int yy=row.top()-(int)scroll;
            if (yy+row.height()<top || yy>bottom) continue;
            int i=row.index(); boolean over=mouseX>=x+10&&mouseX<x+w-12&&mouseY>=Math.max(top,yy)&&mouseY<Math.min(bottom,yy+row.height());
            hover[i]+=(over?1-hover[i]:-hover[i])*.22F;
            g.fillGradient(x+10,yy,x+w-12,yy+row.height(),0xE022170D+(int)(hover[i]*20)*0x00010100,0xE0120A04);
            g.renderOutline(x+10,yy,w-22,row.height(),over?GOLD:0xFF5A4530);
            RouletteItem kind=token==null?RouletteItem.values()[i]:choices.get(i).kind();
            g.renderItem(RouletteItems.stack(kind),x+17,yy+9);
            fitted(g,Component.translatable(kind.titleKey()),x+42,yy+7,w-90,TEXT);
            if (token!=null) {
                Component count=Component.translatable("roulette.ui.count",choices.get(i).count());
                g.drawString(font,count,x+w-19-font.width(count),yy+7,GOLD,false);
            }
            int textY=yy+21;
            for (var line:font.split(Component.translatable(kind.descriptionKey()),Math.max(30,w-64))) {
                g.drawString(font,line,x+42,textY,0xFFC8B898,false); textY+=11;
            }
        }
        if (rows.isEmpty()) g.drawString(font,Component.translatable("roulette.hint.no_choices"),x+14,top+8,MUTED,false);
        g.disableScissor();
        if (maxScroll>0) {
            int track=bottom-top, thumb=Math.max(18,track*track/(track+maxScroll));
            int yy=top+(int)((track-thumb)*scroll/maxScroll);
            g.fill(x+w-7,top,x+w-4,bottom,0x553B2C19); g.fill(x+w-7,yy,x+w-4,yy+thumb,GOLD);
        }
        boolean cancel=mouseX>=x+10&&mouseX<x+w-10&&mouseY>=y+h-26&&mouseY<y+h-7;
        g.fill(x+10,y+h-26,x+w-10,y+h-7,cancel?0x88533D1C:0x88322315);
        Component label=Component.translatable(token==null?"roulette.ui.close":"roulette.ui.cancel");
        g.drawString(font,label,x+(w-font.width(label))/2,y+h-20,cancel?GOLD:TEXT,false);
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if (button!=0) return super.mouseClicked(mouseX,mouseY,button);
        if (mouseX>=x+10&&mouseX<x+w-10&&mouseY>=y+h-26&&mouseY<y+h-7) { click(); onClose(); return true; }
        if (token!=null && !sent && valid() && mouseX>=x+10&&mouseX<x+w-12&&mouseY>=top&&mouseY<bottom) {
            for (Row row:rows) if (mouseY>=row.top()-scroll&&mouseY<row.top()-scroll+row.height()) {
                sent=true;
                ClientPlayNetworking.send(new RouletteNetworking.Select(pos,session,token,choices.get(row.index()).slot()));
                click(); onClose(); return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        scroll=Mth.clamp(scroll-vertical*30,0,maxScroll); return true;
    }

    private void click() { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),1F)); }
}
