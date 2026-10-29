package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import java.util.*;

/** 私人契约说明与不可逆退场确认；只读取服务端进度。 */
public final class EchoContractScreen extends Screen {
    private final Screen parent;
    private UUID match;
    private SreButton retire;
    private int x,y,w,h;
    private long openedAt;
    public EchoContractScreen(Screen parent) { super(Component.translatable("echo.contract.title")); this.parent=parent; }
    @Override protected void init() {
        if(openedAt==0) { openedAt=System.currentTimeMillis(); minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1F)); }
        w=Math.min(440,width-16); h=Math.min(300,height-16); x=(width-w)/2; y=(height-h)/2;
        var d=EchoBagClient.data(); if(d!=null) match=d.matchId();
        retire=button(Component.translatable("echo.contract.retire"),x+8,y+h-48,w-16,() -> minecraft.setScreen(new ConfirmScreen(ok -> {
            minecraft.setScreen(this); if(ok) EchoBagClient.send(EchoInventoryPayload.Operation.RETIRE,null,null,1);
        },Component.translatable("echo.contract.retire"),Component.translatable("echo.contract.retire_warning"))));
        button(Component.translatable("echo.ui.back"),x+w-70,y+h-24,62,this::onClose); tick();
    }
    private SreButton button(Component label,int x,int y,int width,Runnable run) {
        var b=new SreButton(label,ignored -> run.run()); b.setX(x); b.setY(y); b.setWidth(width); b.setHeight(20); return addRenderableWidget(b);
    }
    @Override public void tick() {
        var d=EchoBagClient.data(); if(d==null || d.matchId()==null || match!=null && !match.equals(d.matchId())) { onClose(); return; }
        if(minecraft.player.isSpectator()) { minecraft.setScreen(null); return; }
        if(retire!=null) retire.active=d.contractProgress()!=null && d.contractProgress().retireReady() && d.life().standing() && !d.controlled()
                && !EchoBagClient.waiting() && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        float p=EchoAnim.openProgress(openedAt,180);
        g.fillGradient(0,0,width,height,EchoAnim.fade(0xE0081018,p),EchoAnim.fade(0xE018120A,p));
        g.fill(x,y,x+w,y+h,EchoAnim.fade(0xF01A1008,p)); g.renderOutline(x,y,w,h,EchoAnim.fade(0xFF8B6914,p));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        super.render(g,mx,my,partial);
        float fade=EchoAnim.openProgress(openedAt,180);
        g.drawString(font,title,x+8,y+8,EchoAnim.fade(0xFFD4AF37,fade),false);
        var d=EchoBagClient.data(); if(d==null || d.contract()==null || d.contractProgress()==null) return;
        var p=d.contractProgress(); var id=d.contract().name().toLowerCase(Locale.ROOT);
        var content=Component.translatable("echo.contract."+id+".name").copy().append("\n\n").append(Component.translatable("echo.contract."+id+".desc"))
                .append("\n\n").append(Component.translatable("echo.contract.progress",Component.translatable("echo.contract.state."+p.status().toLowerCase(Locale.ROOT)),p.primary(),p.secondary(),d.blackCargoCount()));
        if(!p.dropoff().isEmpty()) content.append("\n").append(Component.translatable("echo.contract.dropoff",p.dropoff()));
        content.append("\n\n").append(Component.translatable("echo.contract.retire_help"));
        g.enableScissor(x+8,y+28,x+w-8,y+h-58); int n=0;
        for(var line:font.split(content,w-16)) g.drawString(font,line,x+8,y+28+10*n++,0xFFFFF4DC,false);
        g.disableScissor();
        if(EchoBagClient.waiting() || !d.requestError().isEmpty()) g.drawString(font,Component.translatable(EchoBagClient.waiting()?"echo.ui.waiting":"echo.error."+d.requestError().toLowerCase(Locale.ROOT)),x+8,y+h-21,0xFFE06B65,false);
    }
}
