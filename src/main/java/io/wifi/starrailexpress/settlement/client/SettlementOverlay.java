package io.wifi.starrailexpress.settlement.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.utils.RoleUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** GameRenderer 尾部唯一绘制点；漫画分镜不依赖可被取消的普通 HUD。 */
public final class SettlementOverlay {
    private static final int INK=0xFF171719, PAPER=0xFFE6DCC5, SURFACE=0xFFB8AA8C, RED=0xFF6C2323, MUTED=0xFF413C32;
    private static final SettlementNameplateLayout nameplates=new SettlementNameplateLayout();
    static void clear() { nameplates.clear(); }
    private SettlementOverlay() { }

    public static void render(float partialTick) {
        if (!SettlementClient.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        // 后处理及 HUD 共用状态保护，防止污染 Iris、原版后处理或下帧的 GUI。
        try (SettlementRenderState ignored = new SettlementRenderState()) {
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.disableScissor(); RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            if (!SettlementClient.isBlack() && mc.screen == null && mc.getOverlay() == null)
                SettlementPostEffect.render(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, w, h, 0, 1000, 21000),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().identity().translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            try {
                RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
                RenderSystem.disableScissor(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                if (SettlementClient.isBlack() || mc.screen != null || mc.getOverlay() != null)
                    g.fill(0, 0, w, h, 0xFF000000);
                else draw(g, w, h, partialTick);
                g.flush();
            } finally {
                RenderSystem.getModelViewStack().popMatrix(); RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
        }
    }

    private static void draw(GuiGraphics g, int w, int h, float partial) {
        var snapshot=SettlementClient.snapshot();
        if(snapshot==null) { g.fill(0,0,w,h,0xFF000000);return; }
        var frame=SettlementClient.presentation(partial);
        var layout=SettlementClient.layout(partial);
        double time=frame.time(); float ui=layout.ui();
        int edge=Math.max(3,Math.round(4*ui)),margin=Math.round(22*ui);
        g.fill(0,0,w,edge,SURFACE);g.fill(0,h-edge,w,h,SURFACE);
        g.fill(0,edge,edge,h-edge,SURFACE);g.fill(w-edge,edge,w,h-edge,SURFACE);
        g.renderOutline(edge,edge,w-edge*2,h-edge*2,INK);
        // 深纸色信息带与浅色文字分开定义，压暗大面积底色而不连带压暗字幕。
        g.fill(edge+1,edge+1,w-edge-1,Math.round(25*ui),SURFACE);
        text(g,Component.translatable("settlement.starrailexpress.comic.running"),margin,Math.round(11*ui),
                w-margin*2,.72f*ui,MUTED,false);
        Component chapter=Component.translatable(frame.closing()?"settlement.starrailexpress.comic.next":"settlement.starrailexpress.comic.issue");
        text(g,chapter,w-Math.round(150*ui)-margin,Math.round(11*ui),Math.round(150*ui),.72f*ui,INK,true);
        g.fill(margin,Math.round(25*ui),w-margin,Math.round(25*ui)+1,0x332F3432);

        speedLines(g,w,h,(float)frame.impact(),ui);
        if(time>=SettlementPresentation.POSTER_TICKS+32 && time<320) {
            float fade=envelope(time-SettlementPresentation.POSTER_TICKS,32,12,179,23);
            int pw=Math.min(w-margin*2,Math.round(370*ui)),x=(w-pw)/2;
            text(g,Component.translatable("settlement.starrailexpress.comic.chapter"),x,Math.round(30*ui),
                    pw,.65f*ui,tint(PAPER,fade*.75f),true);
            text(g,Component.literal(SettlementClient.title().getString()).withStyle(ChatFormatting.BOLD),x,
                    Math.round(43*ui),pw,1.55f*ui,tint(PAPER,fade),true);
        }
        if(time<SettlementPresentation.POSTER_TICKS) g.fill(0,0,w,h,INK);
        if(!frame.closing() && time>=320 && SettlementClient.eventIndex()>=0) drawCaption(g,layout,time);
        if(time>=SettlementPresentation.ENTRANCE_END+8 && !frame.closing() && frame.hero()<.02) drawIdentities(g,layout,frame);

        if(time>=100 && !frame.closing()) {
            int pw=Math.round(155*ui),x=w-margin-pw,y=h-Math.round(16*ui);
            // 跳过签沿用深纸色，避免画面下沿保留一整条刺眼的米白。
            g.fill(x-4,y-3,w-margin+4,y+Math.round(10*ui),tint(SURFACE,.87f));
            text(g,Component.translatable("settlement.starrailexpress.hud.skip"),x,y,pw,.72f*ui,MUTED,true);
            float held=Math.min(1,SettlementClient.holdTicks()/30f);
            if(held>0) g.fill(x,y+Math.round(10*ui),x+Math.round(pw*held),y+Math.round(12*ui),RED);
        }
        shutters(g,w,h,time,snapshot.durationTicks());
        if(time<SettlementPresentation.POSTER_TICKS) drawMvp(g,layout,(float)frame.hero(),time+202);
        if(frame.closing()) {
            float fade=envelope(time,snapshot.durationTicks()-58,18,snapshot.durationTicks()-22,22);
            int pw=Math.min(w-margin*2,Math.round(264*ui)),ph=Math.round(72*ui),x=(w-pw)/2,y=(h-ph)/2;
            paperPanel(g,x,y,pw,ph,fade,ui);
            text(g,Component.translatable("settlement.starrailexpress.hud.curtain_call").withStyle(ChatFormatting.BOLD),
                    x+10,y+Math.round(17*ui),pw-20,1.55f*ui,tint(INK,fade),true);
            text(g,Component.translatable("settlement.starrailexpress.comic.next"),x+10,y+Math.round(45*ui),pw-20,.8f*ui,tint(MUTED,fade),true);
        }
        float black=(float)Math.max(1-SettlementClient.smooth(time/28),SettlementClient.smooth((time-snapshot.durationTicks()+20)/20));
        if(black>0) g.fill(0,0,w,h,tint(0xFF000000,black));
    }

    private static void drawMvp(GuiGraphics g,SettlementPresentation.Layout layout,float fade,double time) {
        var mvp=SettlementClient.byId.get(SettlementClient.snapshot().mvp());
        if(mvp==null||fade<.01f) return;
        int w=layout.width(),h=layout.height(),edge=Math.max(4,Math.round(5*layout.ui()));
        float ui=layout.ui();var poster=SettlementPresentation.heroLayout(w,h);
        g.enableScissor(edge,edge,w-edge,h-edge);
        g.fill(edge,edge,w-edge,h-edge,tint(0xFF101E28,fade));
        int top=(int)(h*.145),bottom=(int)(h*.91);
        g.fill(edge,top,w-edge,bottom,tint(0xFFB92140,fade));
        g.fillGradient(edge,top,w-edge,bottom,tint(0x006B1930,0),tint(0xFF6B1930,fade*.45f));
        // 巨幅字是背景版式，不是再给场上每个人套一块浮动面板。
        var font=Minecraft.getInstance().font;
        g.pose().pushPose();g.pose().translate(w*.16-(1-SettlementPresentation.easeOut((time-204)/14))*w*.12,h*.19,0);
        g.pose().scale(w*.78f/Math.max(1,font.width("MVP")),h*.57f/font.lineHeight,1);
        g.drawString(font,Component.literal("MVP").withStyle(ChatFormatting.BOLD),0,0,tint(PAPER,fade*.13f),false);
        g.pose().popPose();
        text(g,Component.translatable("settlement.starrailexpress.hero.banner"),Math.round(w*.055f),Math.round(h*.175f),
                Math.round(w*.72f),.82f*ui,tint(PAPER,fade*.8f),false);
        // 确定性的印刷网点，避免随机闪烁和高频动画噪点。
        int step=Math.max(8,Math.round(11*ui));
        for(int y=top+step*3;y<bottom;y+=step) for(int x=w/2;x<w-edge;x+=step)
            if((x/step+y/step)%3==0) g.fill(x,y,x+1,y+1,tint(PAPER,fade*.14f));
        g.fill(Math.round(w*.055f),top-2,Math.round(w*.29f),top,tint(PAPER,fade));
        text(g,Component.translatable("settlement.starrailexpress.comic.running"),Math.round(w*.055f),Math.round(h*.066f),
                Math.round(w*.62f),.82f*ui,tint(PAPER,fade*.8f),false);
        g.flush();
        SettlementHeroRenderer.render(g,mvp,poster.portrait(),time,fade,ui);
        var r=poster.info();var copy=SettlementPresentation.heroText(poster,ui);
        int x=(int)r.x(),y=(int)r.y(),width=(int)r.w(),statsY=(int)copy.stats().y();
        if(poster.stacked()) g.fill(x-6,y-6,x+width+6,(int)r.bottom()+2,tint(0xFF101E28,fade*.93f));
        text(g,Component.translatable("settlement.starrailexpress.hero.award").withStyle(ChatFormatting.BOLD),
                (int)copy.award().x(),(int)copy.award().y(),(int)copy.award().w(),
                (poster.stacked()?1f:2.65f)*ui,tint(PAPER,fade),false);
        labelText(g,mvp.fact.name(),(int)copy.name().x(),(int)copy.name().y(),(int)copy.name().w(),
                (poster.stacked()?1.05f:1.7f)*ui,tint(PAPER,fade));
        ResourceLocation role=ResourceLocation.tryParse(mvp.fact.role());
        String roleName=role==null?Component.translatable("settlement.starrailexpress.unknown_role").getString():RoleUtils.getRoleName(role).getString();
        text(g,Component.literal(roleName).withStyle(ChatFormatting.BOLD),(int)copy.role().x(),(int)copy.role().y(),
                (int)copy.role().w(),copy.roleScale(),tint(PAPER,fade),false);
        if(!poster.stacked()) g.fill(x,statsY-Math.round(10*ui),x+width,statsY-Math.round(9*ui),tint(PAPER,fade*.45f));
        text(g,Component.literal(SettlementClient.title().getString()).withStyle(ChatFormatting.BOLD),
                (int)copy.victory().x(),(int)copy.victory().y(),(int)copy.victory().w(),copy.victoryScale(),tint(PAPER,fade),false);
        int[] values={mvp.fact.score(),mvp.fact.kills(),mvp.fact.tasks()};String[] keys={"score","kills","tasks"};
        int col=width/3;double count=SettlementPresentation.easeOut((time-222)/30);
        for(int i=0;i<3;i++) {
            int left=x+i*col;float a=fade*(float)SettlementPresentation.smooth((time-218-i*4)/10);
            text(g,Component.literal(Long.toString(Math.round(values[i]*count))).withStyle(ChatFormatting.BOLD),left,statsY,col,
                    (poster.stacked()?1.35f:2.1f)*ui,tint(PAPER,a),false);
            text(g,Component.translatable("settlement.starrailexpress.hud."+keys[i]),left,
                    (int)copy.statLabels().y(),col,.72f*ui,tint(PAPER,a*.8f),false);
        }
        g.disableScissor();
    }

    private static void drawCaption(GuiGraphics g,SettlementPresentation.Layout layout,double time) {
        var snapshot=SettlementClient.snapshot(); int index=SettlementClient.eventIndex();
        var event=snapshot.events().get(index); var r=layout.caption(); float ui=layout.ui();
        int x=(int)r.x(),y=(int)r.y(),w=(int)r.w(),h=(int)r.h(),pad=Math.round(11*ui);
        g.fill(x+2,y+3,x+w+2,y+h+3,0x33272B29);
        g.fill(x,y,x+w,y+h,0xF22B302E);
        g.fill(x,y,x+Math.max(2,Math.round(3*ui)),y+h,RED);
        g.fill(x,y,x+w,y+1,0x66F3EDDD);
        var category=SettlementPresentation.category(event.type());
        Component label=Component.translatable("settlement.starrailexpress.comic.category."+category.name().toLowerCase(Locale.ROOT));
        long seconds=Math.max(0,event.time())/1000;
        Component stamp=Component.literal(String.format(Locale.ROOT,"%02d:%02d   /   %02d · %02d",seconds/60,seconds%60,index+1,snapshot.events().size()));
        text(g,label,x+pad,y+Math.round(5*ui),Math.round(110*ui),.70f*ui,0xFFE4B79D,false);
        text(g,stamp,x+w-Math.round(170*ui)-pad,y+Math.round(5*ui),Math.round(170*ui),.65f*ui,0xFFB9BDAF,true);
        int top=y+Math.round(19*ui),visible=h-Math.round(24*ui);
        var lines=SettlementClient.captionLines();
        double age=time-320-index*snapshot.eventTicks();
        double scroll=SettlementPresentation.scroll(age,snapshot.eventTicks(),lines.size()*12*ui,visible);
        g.enableScissor(x+pad,top,x+w-pad,top+visible);
        g.pose().pushPose();g.pose().translate(x+pad,top-scroll,0);g.pose().scale(ui,ui,1);
        for(int i=0;i<lines.size();i++) g.drawString(Minecraft.getInstance().font,lines.get(i),0,i*12,PAPER,false);
        g.pose().popPose();g.disableScissor();
        float progress=(float)(time-320)/Math.max(1,snapshot.events().size()*snapshot.eventTicks());
        g.fill(x,y+h-1,x+Math.round(w*Mth.clamp(progress,0,1)),y+h,RED);
    }

    /** 姓名最多三条，职业集中在固定信息栏；占用面积不随总人数增加。 */
    private static void drawIdentities(GuiGraphics g,SettlementPresentation.Layout layout,SettlementPresentation.Frame frame) {
        var snapshot=SettlementClient.snapshot();
        var event=SettlementClient.eventIndex()<0?null:snapshot.events().get(SettlementClient.eventIndex());
        var identities=SettlementPresentation.identityActors(snapshot,frame,event);
        var rail=layout.identity();float ui=layout.ui();
        int x=(int)rail.x(),y=(int)rail.y(),w=(int)rail.w(),h=(int)rail.h();
        if(!identities.isEmpty()) {
            g.fill(x,y,x+w,y+h,0xE3252C29);
            int col=w/identities.size(),pad=Math.round(7*ui);
            g.enableScissor(x,y,x+w,y+h);
            for(int i=0;i<identities.size();i++) {
                var actor=SettlementClient.byId.get(identities.get(i));
                if(actor==null) continue;
                int left=x+i*col,nameW=(int)(col*.51)-pad;
                int roleColor=0xFF000000|SettlementCaption.readableColor(actor.fact.color()&0xFFFFFF);
                if(i>0) g.fill(left,y+Math.round(4*ui),left+1,y+h-Math.round(4*ui),0x556F796E);
                g.fill(left+Math.round(3*ui),y+Math.round(5*ui),left+Math.round(4*ui),y+h-Math.round(5*ui),roleColor);
                String name=(actor.fact.id().equals(snapshot.mvp())?"MVP · ":"")
                        +(SettlementClient.isDeathMarked(actor)?"× ":"")+actor.fact.name();
                int baseline=y+Math.round(5*ui);
                labelText(g,name,left+pad,baseline,nameW,.78f*ui,PAPER);
                ResourceLocation role=ResourceLocation.tryParse(actor.role);
                String roleName=role==null?Component.translatable("settlement.starrailexpress.unknown_role").getString():RoleUtils.getRoleName(role).getString();
                labelText(g,roleName,left+pad+nameW,baseline,col-nameW-pad*2,.78f*ui,roleColor);
            }
            g.disableScissor();
        }
        if(SettlementStageRenderer.labelsAt!=SettlementClient.tick() || SettlementStageRenderer.labelsWidth!=layout.width()
                || SettlementStageRenderer.labelsHeight!=layout.height()) return;
        var selected=SettlementPresentation.nameActors(snapshot,frame,event);
        Object shot=frame.time()<320?"cast":"replay/"+frame.primary();
        nameplates.begin(shot,layout.width(),layout.height());
        nameplates.retain(selected);
        List<SettlementPresentation.Rect> occupied=new ArrayList<>();
        for(var l:SettlementStageRenderer.labels) if(l.actor().fact.winner()) occupied.add(l.head().grow(2*ui));
        double top=(frame.time()<320?62:32)*ui;
        var screen=new SettlementPresentation.Rect(12*ui,top,layout.width()-24*ui,Math.max(0,rail.y()-5*ui-top));
        var font=Minecraft.getInstance().font;
        for(var id:selected) {
            var label=SettlementStageRenderer.labels.stream().filter(l -> l.actor().fact.id().equals(id)).findFirst().orElse(null);
            if(label==null) continue;
            var actor=label.actor();boolean mvp=id.equals(snapshot.mvp());
            String name=(mvp?"MVP · ":"")+(SettlementClient.isDeathMarked(actor)?"× ":"")+actor.fact.name();
            double width=Math.min(SettlementPresentation.NAME_MARKER_WIDTH*ui,font.width(name)*.74f*ui+8*ui);
            var box=nameplates.place(id,label.x(),label.head().y()-9*ui,width,
                    SettlementPresentation.NAME_MARKER_HEIGHT*ui,screen,occupied,4*ui,false);
            if(box==null) continue;
            int bx=(int)box.x(),by=(int)box.y(),bw=(int)box.w(),bh=(int)box.h();
            // 只画一行小字的底衬，不再画姓名/职业双行大卡片和跨过人物的引导线。
            g.fill(bx,by,bx+bw,by+bh,0xC9262E2A);
            labelText(g,name,bx+Math.round(4*ui),by+Math.round(2*ui),bw-Math.round(8*ui),.74f*ui,PAPER);
        }
    }

    private static void labelText(GuiGraphics g,String value,int x,int y,int width,float scale,int color) {
        var font=Minecraft.getInstance().font;
        if(font.width(value)*scale>width) value=font.plainSubstrByWidth(value,Math.max(1,(int)(width/scale)-font.width("…")))+"…";
        text(g,Component.literal(value),x,y,width,scale,color,false);
    }

    /** 纸片、错位墨影与局部网点构成分镜标题块；每个元素共享淡入透明度。 */
    private static void paperPanel(GuiGraphics g, int x, int y, int w, int h, float alpha, float ui) {
        if (alpha <= .01f) return;
        int shift=Math.max(2,Math.round(3*ui)),cut=Math.max(4,Math.round(7*ui));
        g.fill(x+shift,y+shift,x+w+shift,y+h+shift,tint(INK,alpha*.20f));
        g.fill(x,y+cut,x+w,y+h,tint(SURFACE,alpha*.98f));
        g.fill(x,y,x+w-cut,y+cut,tint(SURFACE,alpha*.98f));
        g.fill(x,y,x+w-cut,y+1,tint(INK,alpha*.75f));
        g.fill(x,y,x+2,y+h,tint(INK,alpha*.85f));
        g.fill(x,y+h-2,x+w,y+h,tint(INK,alpha*.80f));
        g.fill(x+w-1,y+cut,x+w,y+h,tint(INK,alpha*.65f));
        g.flush();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bevel=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        quad(bevel,g.pose().last().pose(),x+w-cut,y,x+w-cut,y+cut,x+w,y+cut,x+w-cut,y,tint(SURFACE,alpha*.98f));
        quad(bevel,g.pose().last().pose(),x+w-cut,y,x+w-cut-1,y+1,x+w-1,y+cut+1,x+w,y+cut,tint(INK,alpha*.75f));
        BufferUploader.drawWithShader(bevel.buildOrThrow());
        int step = Math.max(4, Math.round(6 * ui)), dot = Math.max(1, Math.round(ui));
        // 网点只放在卡片右侧的小三角内，固定采样，不随帧随机闪烁。
        for (int row = 0; row < 6; row++) for (int col = 0; col < 6 - row; col++) {
            int px = x + w - 5 - col * step, py = y + 5 + row * step;
            if (px > x && py + dot < y + h)
                g.fill(px, py, px + dot, py + dot, tint(INK, alpha * .12f));
        }
    }

    /** 斜切黑页从两侧揭开/合上，不再保留与场景割裂的剧场侧帘。 */
    private static void shutters(GuiGraphics g, int w, int h, double time, int duration) {
        double opened = SettlementPresentation.curtainOpen(time)
                * (1 - SettlementClient.smooth((time - duration + 66) / 46));
        float side = (float) (w * .65 * (1 - opened));
        if (side < .5f) return;
        float skew = (float) (w * .045 * (1 - opened));
        g.flush();
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        quad(b, m, 0, 0, 0, h, side - skew, h, side + skew, 0, INK);
        quad(b, m, w - side - skew, 0, w - side + skew, h, w, h, w, 0, INK);
        quad(b, m, side + skew - 3, 0, side - skew - 3, h, side - skew, h, side + skew, 0, PAPER);
        quad(b, m, w - side - skew, 0, w - side + skew, h, w - side + skew + 3, h, w - side - skew + 3, 0, PAPER);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** 仅边缘出现短促放射线；合批绘制，不覆盖人物中央，不做整屏爆闪。 */
    private static void speedLines(GuiGraphics g, int w, int h, float strength, float ui) {
        if (strength <= .02f) return;
        g.flush();
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        float cx = w * .56f, cy = h * .50f, radius = (float) Math.hypot(w, h);
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / 28 + .035 * Math.sin(i * 7.3);
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float innerX = cx + dx * w * (.32f + .04f * (i % 3));
            float innerY = cy + dy * h * (.34f + .03f * (i % 4));
            float outerX = cx + dx * radius, outerY = cy + dy * radius;
            float thick = (2 + i % 3) * ui * strength;
            quad(b, m, innerX - dy * .3f, innerY + dx * .3f, outerX - dy * thick, outerY + dx * thick,
                    outerX + dy * thick, outerY - dx * thick, innerX + dy * .3f, innerY - dx * .3f,
                    tint(PAPER, strength * .55f));
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1,
                             float x2, float y2, float x3, float y3, int color) {
        b.addVertex(m, x0, y0, 0).setColor(color); b.addVertex(m, x1, y1, 0).setColor(color);
        b.addVertex(m, x2, y2, 0).setColor(color); b.addVertex(m, x3, y3, 0).setColor(color);
    }

    private static float envelope(double time, double start, double in, double end, double out) {
        return (float) (SettlementClient.smooth((time - start) / in) * (1 - SettlementClient.smooth((time - end) / out)));
    }


    private static int tint(int color, float alpha) {
        return Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24 | color & 0xFFFFFF;
    }

    private static void text(GuiGraphics g, Component text, int x, int y, int width, float preferredScale,
                             int color, boolean center) {
        if ((color >>> 24) < 4 || width <= 0) return;
        var font = Minecraft.getInstance().font;
        float scale = Math.min(preferredScale, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(center ? x + width / 2f : x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, center ? -font.width(text) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

}
