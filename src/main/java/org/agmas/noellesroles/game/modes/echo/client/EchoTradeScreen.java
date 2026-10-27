package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import java.util.*;

/** 双方报价面板；本地只编辑待提交实例列表，不预测锁定或成交。 */
public final class EchoTradeScreen extends Screen {
    private final Screen parent;
    private final List<UUID> selected=new ArrayList<>();
    private UUID match,tradeId;
    private long offerVersion=-1;
    private String signature="";
    private int x,y,w,h;
    private int targetPage;
    private long openedAt;
    public EchoTradeScreen(Screen parent) { super(Component.translatable("echo.trade.title")); this.parent=parent; }
    @Override protected void init() {
        clearWidgets(); if(openedAt==0) { openedAt=System.currentTimeMillis(); minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1F)); } w=Math.min(620,width-12); h=Math.min(340,height-12); x=(width-w)/2; y=(height-h)/2;
        var d=EchoBagClient.data(); if(d==null || d.bag()==null) return;
        if(match==null) match=d.matchId(); var trade=d.trade();
        boolean enabled=d.phase().acceptsActions() && d.life().standing() && !d.controlled() && !EchoBagClient.waiting()
                && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen();
        if(trade==null) {
            selected.clear(); tradeId=null; offerVersion=-1;
            var candidates=minecraft.level.players().stream().filter(p -> p!=minecraft.player && !p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(minecraft.player)<=d.tradeRange()*d.tradeRange() && minecraft.player.hasLineOfSight(p))
                    .sorted(Comparator.comparingDouble(p -> p.distanceToSqr(minecraft.player))).toList();
            int pageSize = Math.max(2, Math.min(6, (h - 110) / 22) * 2);
            int pages = Math.max(1, (candidates.size() + pageSize - 1) / pageSize);
            targetPage = Math.max(0, Math.min(targetPage, pages - 1));
            if (pages > 1) {
                button(Component.literal("<"), x+8, y+h-46, 32, () -> { targetPage--; init(); }).active=targetPage>0;
                button(Component.literal(">"), x+80, y+h-46, 32, () -> { targetPage++; init(); }).active=targetPage<pages-1;
            }
            int row=0;
            for(var p:candidates.subList(targetPage * pageSize, Math.min(candidates.size(), (targetPage + 1) * pageSize))) {
                int column=row%2,at=row/2;
                button(Component.translatable("echo.trade.propose",p.getName()),x+8+column*(w/2),y+58+at*22,w/2-16,
                        () -> EchoBagClient.send(EchoInventoryPayload.Operation.TRADE_PROPOSE,p.getUUID(),null,0)).active=enabled;
                row++;
            }
        } else {
            if(!trade.id().equals(tradeId) || trade.version()!=offerVersion) {
                selected.clear(); trade.own().forEach(i -> selected.add(i.instanceId())); tradeId=trade.id(); offerVersion=trade.version();
            }
            boolean editing=trade.status().equals("EDITING");
            int left=(w-24)/2;
            for(int slot=0;slot<6;slot++) {
                var cell=d.bag().slots().get(slot); int count=cell.items().size();
                if(count==0) continue;
                for(int n=0;n<count;n++) {
                    var item=cell.items().get(n); UUID lock=d.bag().locks().get(item.instanceId());
                    var label=Component.literal(selected.contains(item.instanceId())?"✓ ":"").append(name(item));
                    int rowHeight=Math.min(21,Math.max(12,(h-130)/6));
                    var b=button(label,x+8+n*(left/count),y+58+slot*rowHeight,left/count-3,() -> { if(!selected.remove(item.instanceId()) && selected.size()<2) selected.add(item.instanceId()); init(); });
                    b.setHeight(Math.min(18,rowHeight-2));
                    b.active=enabled && editing && (lock==null || lock.equals(trade.id()));
                    var info=name(item).copy();
                    if(item.kind()!=EchoResourceInstance.Kind.PACK) {
                        var card=d.rules().card(item.definitionId()); info.append("\n").append(EchoCardText.description(card));
                        if(!card.usableBy(d.faction())) info.append("\n").append(Component.translatable("echo.ui.wrong_faction"));
                    }
                    if(lock!=null) info.append("\n").append(Component.translatable("echo.ui.locked"));
                    b.setTooltip(Tooltip.create(info));
                }
            }
            if(trade.status().equals("PROPOSED")) {
                button(Component.translatable(trade.proposer()?"echo.trade.await_accept":"echo.trade.accept"),x+8,y+h-68,w/2-12,
                        () -> EchoBagClient.send(EchoInventoryPayload.Operation.TRADE_ACCEPT,null,null,0)).active=enabled && !trade.proposer();
            } else {
                button(Component.translatable("echo.trade.save_offer"),x+8,y+h-68,w/2-12,() -> EchoBagClient.tradeOffer(List.copyOf(selected))).active=enabled && editing;
                button(Component.translatable("echo.trade.clear_offer"),x+w/2+4,y+h-68,w/2-12,() -> EchoBagClient.tradeOffer(List.of())).active=enabled && editing;
                var confirm=button(Component.translatable(trade.ownConfirmed()?"echo.trade.confirmed":"echo.trade.confirm"),x+8,y+h-44,w/2-12,
                        () -> EchoBagClient.send(EchoInventoryPayload.Operation.TRADE_CONFIRM,null,null,0));
                confirm.active=enabled && editing && !trade.ownConfirmed() && trade.layoutError().isEmpty()
                        && new HashSet<>(selected).equals(new HashSet<>(trade.own().stream().map(EchoResourceInstance::instanceId).toList()));
                confirm.setTooltip(Tooltip.create(Component.translatable("echo.trade.confirm_help")));
            }
            button(Component.translatable("echo.trade.cancel"),x+w/2+4,y+h-44,w/2-12,
                    () -> EchoBagClient.send(EchoInventoryPayload.Operation.TRADE_CANCEL,null,null,0));
        }
        button(Component.translatable("echo.ui.back"),x+w-64,y+h-22,56,this::onClose);
        signature=signature();
    }
    private SreButton button(Component label,int x,int y,int width,Runnable action) {
        var b=new SreButton(label,ignored -> action.run()); b.setX(x); b.setY(y); b.setWidth(width); b.setHeight(18); return addRenderableWidget(b);
    }
    private String signature() {
        var d=EchoBagClient.data(); return d==null?"":d.trade()+":"+(d.bag()==null?0:d.bag().revision())+":"+d.acknowledged()+":"+EchoBagClient.waiting()+":"+d.life()+":"+d.controlled()+":"+d.phase()+":"+io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen();
    }
    @Override public void tick() {
        var d=EchoBagClient.data(); if(d==null || d.matchId()==null || match!=null && !match.equals(d.matchId())) { onClose(); return; }
        if(!signature.equals(signature()) || d.trade()==null && minecraft.player.tickCount%20==0) init();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        float fade=EchoAnim.openProgress(openedAt,180);
        g.fillGradient(0,0,width,height,EchoAnim.fade(0xE0081018,fade),EchoAnim.fade(0xE018120A,fade));
        g.fill(x,y,x+w,y+h,EchoAnim.fade(0xF01A1008,fade)); g.renderOutline(x,y,w,h,EchoAnim.fade(0xFF8B6914,fade));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        super.render(g,mx,my,partial);
        float fade=EchoAnim.openProgress(openedAt,180);
        g.drawString(font,title,x+8,y+8,EchoAnim.fade(0xFFD4AF37,fade),false);
        var d=EchoBagClient.data(); if(d==null || d.bag()==null) return;
        var t=d.trade();
        if(t==null) {
            text(g,Component.translatable("echo.trade.target_help"),x+8,y+26,w-16,28,0xFFFFF4DC);
        } else {
            long remainMs=Math.max(0,t.deadline()-d.projectedTick())*50L;
            int headerColor=EchoAnim.blend(0xFF5EB7D8,0xFFE06B65,EchoAnim.urgency(remainMs,10000));
            text(g,Component.translatable("echo.trade.header",t.peerName(),t.version(),Math.max(0,(t.deadline()-d.projectedTick()+19)/20),t.range()),x+8,y+24,w-16,20,EchoAnim.fade(headerColor,fade));
            int rx=x+w/2+4,rw=w/2-12;
            text(g,Component.translatable("echo.trade.own_offer"),rx,y+48,rw,10,0xFFD4AF37);
            for(int n=0;n<t.own().size();n++) text(g,name(t.own().get(n)),rx,y+61+n*11,rw,11,0xFFFFF4DC);
            text(g,Component.translatable("echo.trade.peer_offer"),rx,y+89,rw,10,0xFFD4AF37);
            for(int n=0;n<t.theirs().size();n++) text(g,name(t.theirs().get(n)),rx,y+102+n*11,rw,11,0xFFFFF4DC);
            text(g,Component.translatable(t.peerConfirmed()?"echo.trade.peer_confirmed":"echo.trade.peer_unconfirmed"),rx,y+129,rw,20,0xFF5EB7D8);
            if(!t.layoutError().isEmpty()) text(g,Component.translatable("echo.error."+t.layoutError().toLowerCase(Locale.ROOT)),rx,y+151,rw,Math.max(10,h-226),0xFFE06B65);
        }
        var message=EchoBagClient.waiting()?Component.translatable("echo.ui.waiting"):d.requestError().isEmpty()?Component.translatable("echo.trade.privacy"):Component.translatable("echo.error."+d.requestError().toLowerCase(Locale.ROOT));
        text(g,message,x+8,y+h-20,w-80,18,d.requestError().isEmpty()?0xFF9E8B6E:0xFFE06B65);
    }
    private void text(GuiGraphics g,Component value,int x,int y,int w,int h,int color) {
        g.enableScissor(x,y,x+w,y+h); int n=0; for(var line:font.split(value,w)) g.drawString(font,line,x,y+10*n++,color,false); g.disableScissor();
    }
    private Component name(EchoResourceInstance item) {
        return Component.translatable(item.kind()==EchoResourceInstance.Kind.PACK?"echo.pack."+item.quality().name().toLowerCase(Locale.ROOT):EchoCardRegistry.require(item.definitionId()).nameKey());
    }
}
