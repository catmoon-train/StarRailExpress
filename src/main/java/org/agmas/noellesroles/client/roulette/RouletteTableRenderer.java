package org.agmas.noellesroles.client.roulette;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.game.minigame.roulette.*;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import static org.agmas.noellesroles.client.roulette.RouletteGeometry.*;

/** 赌桌坐标统一为北侧 front；所有部件一起变换，四朝向不依赖玩家视角。 */
public final class RouletteTableRenderer implements BlockEntityRenderer<DevilRouletteTableEntity> {
    private final EnumMap<RouletteItem, ItemStack> stacks = new EnumMap<>(RouletteItem.class);
    public RouletteTableRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public int getViewDistance() { return 24; }
    @Override public boolean shouldRenderOffScreen(DevilRouletteTableEntity table) { return true; }

    @Override
    public void render(DevilRouletteTableEntity table, float partial, PoseStack p, MultiBufferSource b, int light, int overlay) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || table.getLevel() == null || mc.player.distanceToSqr(Vec3.atCenterOf(table.getBlockPos())) > 24*24) return;
        var state = RouletteClient.observe(table);
        if (state == null) return;
        RouletteView v = state.view;
        float age = v.age(table.getLevel().getGameTime(), partial);
        p.pushPose();
        p.translate(.5, .135, .5);
        p.mulPose(Axis.YP.rotationDegrees(180 - table.getFacing().toYRot()));
        box(p,b,light,DARK,-.22,.002,-.36,.44,.025,.72); // 枪架
        for (int side=0;side<2;side++) side(table,state,side,age,partial,p,b,light,overlay);
        gun(state,age,p,b,light);
        reload(v,age,p,b,light);
        action(table,v,age,p,b,light,overlay);
        if (v.phase() == RoulettePhase.WAITING) {
            text(p,b,Component.translatable("roulette.ui.title"),0,.04,0,0,GOLD,.007F);
            text(p,b,Component.translatable("roulette.hint.sit"),0,.04,.18,0,0xFFFFF4DC,.0045F);
        } else if (v.phase() == RoulettePhase.ENDING) {
            text(p,b,Component.translatable("roulette.ui.winner", v.winner().equals(RouletteView.NONE)
                    ? Component.translatable("roulette.ui.draw") : Component.literal(v.name(v.winner()))),0,.08,0,0,GOLD,.007F);
        }
        p.popPose();
    }

    private void side(DevilRouletteTableEntity table, RouletteClient.State state, int side, float age, float partial,
                      PoseStack p, MultiBufferSource b, int light, int overlay) {
        RouletteView v = state.view;
        UUID player = side == 0 ? v.front() : v.back();
        int hp = side == 0 ? v.frontHealth() : v.backHealth();
        int maxHp = side == 0 ? v.frontMaxHealth() : v.backMaxHealth();
        double healthStart = -((maxHp-1)*.21+.14)/2;
        p.pushPose();
        p.mulPose(Axis.YP.rotationDegrees(side == 0 ? 180 : 0));
        int lit = player.equals(RouletteView.NONE) ? 0 : v.phase() == RoulettePhase.INTRO ? Math.min(hp,(int)(age/3)) : hp;
        for (int i=0;i<maxHp;i++) {
            int color = i<lit ? RED : DARK;
            UUID affected=v.target();
            boolean pulse = v.phase().impact()>=0 && v.delta()!=0 && age>=v.phase().impact()
                    && age<v.phase().impact()+9 && player.equals(affected);
            if (pulse && i<lit) color = v.delta()>0 ? 0xFF72C17B : RED;
            boolean lost=pulse && v.delta()<0 && i>=lit && i<lit-v.delta() && ((int)age%4)<2;
            if (lost) color=RED;
            box(p,b,light,METAL,healthStart+i*.21,.003,1.045,.14,.025,.10);
            box(p,b,i<lit||lost?LightTexture.FULL_BRIGHT:light,color,healthStart+.015+i*.21,.029,1.06,.11,.025,.07);
        }
        Component name = player.equals(RouletteView.NONE) ? Component.translatable("roulette.ui.empty_seat") : Component.literal(v.name(player));
        text(p,b,name,0,.035,1.42,0,player.equals(v.operator())?GOLD:0xFFFFF4DC,.005F);
        List<Integer> items = side == 0 ? v.frontItems() : v.backItems();
        double now = table.getLevel().getGameTime()+partial;
        for (int i=0;i<8;i++) {
            double x = slotX(i), z = slotZ(i);
            box(p,b,light,0xFF433725,x-.19,.001,z-.13,.38,.008,.26);
            if (i >= items.size()) continue;
            RouletteItem kind = RouletteItem.byOrdinal(items.get(i));
            float t = ease((float)((now-state.arrivals[side][i])/6));
            double fromX = side == 0 ? 1.25 : -1.25;
            p.pushPose();
            p.translate(Mth.lerp(t,fromX,x),.10+Math.sin(t*Math.PI)*.17,Mth.lerp(t,0,z));
            p.mulPose(Axis.XP.rotationDegrees(-65));
            drawItem(table,kind,.32F,p,b,light,overlay);
            p.popPose();
        }
        var mc = Minecraft.getInstance();
        int zone=-1;
        if (mc.player != null && player.equals(mc.player.getUUID()) && v.phase()==RoulettePhase.READY
                && player.equals(v.operator()) && mc.hitResult instanceof BlockHitResult hit)
            zone=table.operation(player,hit.getBlockPos());
        for (int i=0;i<3;i++) {
            double x = (i-1)*.88;
            box(p,b,zone==i?LightTexture.FULL_BRIGHT:light,zone==i?GOLD:DARK,x-.39,.003,1.195,.78,.012,.17);
            text(p,b,Component.translatable(i==0?"roulette.ui.self":i==1?"roulette.ui.item_help":"roulette.ui.opponent"),
                    x,.022,1.28,0,zone==i?GOLD:0xFFC8B898,.0045F);
        }
        if (!player.equals(RouletteView.NONE) && player.equals(v.cuffed())) {
            p.pushPose(); p.translate(.78,.12,1.05);
            drawItem(table,RouletteItem.HANDCUFFS,.23F,p,b,light,overlay); p.popPose();
        }
        if (v.phase()==RoulettePhase.HANDOFF && player.equals(state.releasedCuffs) && age<10) {
            p.pushPose(); p.translate(.65+age*.025,.15+Math.sin(age/10*Math.PI)*.2,1.05);
            p.mulPose(Axis.ZP.rotationDegrees(age*15));
            drawItem(table,RouletteItem.HANDCUFFS,.23F*(1-age/12),p,b,light,overlay); p.popPose();
        }
        if (v.phase()!=RoulettePhase.WAITING) text(p,b,Component.translatable("roulette.hud.ammo_remaining",v.remaining()),
                0,.025,.37,0,0xFFC8B898,.004F);
        p.popPose();
    }

    private void gun(RouletteClient.State state, float age, PoseStack p, MultiBufferSource b, int light) {
        RouletteView v=state.view;
        float yaw=RouletteClient.State.operatorYaw(v), lift=0, pump=0, recoil=0, roll=0;
        float actor=v.actor().equals(v.front())?-1:1;
        boolean shortBarrel=v.phase()==RoulettePhase.SHOOT?state.shotSaw:v.saw();
        if (v.phase()==RoulettePhase.HANDOFF) yaw=Mth.rotLerp(ease(age/Math.max(1,v.duration())),state.handoffFrom,yaw);
        if (v.phase()==RoulettePhase.SHOOT) {
            float take=ease(age/4), down=1-ease((age-22)/6);
            lift=take*down;
            float base=v.actor().equals(v.front())?180:0;
            float aim=v.actor().equals(v.target())?180:0;
            yaw=base+aim*ease(age/5)*(1-ease((age-20)/5));
            recoil=v.shell()==1 ? (1-ease((age-9)/4))*(age>=9?1:0) : 0;
            pump=(float)Math.sin(Mth.clamp((age-14)/7,0,1)*Math.PI);
        }
        RouletteItem active=v.phase()==RoulettePhase.ADRENALINE?v.stolen():v.item();
        float itemAge=v.phase()==RoulettePhase.ADRENALINE?age-15:age;
        if ((v.phase()==RoulettePhase.ITEM || v.phase()==RoulettePhase.ADRENALINE) && active==RouletteItem.BEER)
            pump=(float)Math.sin(Mth.clamp((itemAge-13)/9,0,1)*Math.PI);
        if ((v.phase()==RoulettePhase.ITEM || v.phase()==RoulettePhase.ADRENALINE) && active==RouletteItem.INVERTER)
            roll=360*ease((itemAge-5)/14);
        p.pushPose();
        p.translate(0,.11+lift*.45,actor*lift*.42);
        p.mulPose(Axis.YP.rotationDegrees(yaw));
        p.translate(0,0,recoil*.10);
        p.mulPose(Axis.XP.rotationDegrees(-recoil*9));
        p.mulPose(Axis.ZP.rotationDegrees(roll));
        shotgun(p,b,light,shortBarrel,pump);
        if (v.phase()==RoulettePhase.SHOOT && v.shell()==1 && age>=9 && age<11.5) {
            double tip=shortBarrel?-.4:-.73;
            box(p,b,LightTexture.FULL_BRIGHT,0xFFFFDF87,-.065,.01,tip-.22,.13,.10,.22);
            box(p,b,LightTexture.FULL_BRIGHT,0xFFFFAA33,-.025,-.03,tip-.12,.05,.18,.12);
        }
        boolean ejectShot=v.phase()==RoulettePhase.SHOOT && age>=16 && age<25;
        boolean ejectBeer=(v.phase()==RoulettePhase.ITEM || v.phase()==RoulettePhase.ADRENALINE)
                && active==RouletteItem.BEER && itemAge>=14 && itemAge<24;
        if (v.shell()>=0 && (ejectShot || ejectBeer)) {
            float t=(ejectShot?age-16:itemAge-14)/9;
            p.pushPose(); p.translate(.10+t*.48,.04+Math.sin(t*Math.PI)*.23,-.08+t*.1);
            p.mulPose(Axis.ZP.rotationDegrees(t*420)); shell(p,b,light,v.shell()); p.popPose();
        }
        p.popPose();
    }

    private void reload(RouletteView v,float age,PoseStack p,MultiBufferSource b,int light) {
        if (v.phase()!=RoulettePhase.RELOAD) return;
        if (age<16) {
            // 这里只显示红蓝分组构成，绝不把它当作真实装填顺序。
            for (int kind=0;kind<2;kind++) for (int i=0;i<(kind==1?v.live():v.blanks());i++) {
                p.pushPose(); p.translate((kind==1?-.55:.55)+(i%2)*.10,.04,-.7+(i/2)*.16);
                shell(p,b,light,kind); p.popPose();
            }
        } else {
            box(p,b,light,DARK,-.81,.01,-.85,.38,.13,.7);
            box(p,b,light,DARK,.43,.01,-.85,.38,.13,.7);
            if (age>=20 && age<43) {
                float t=((age-20)%6)/6;
                p.pushPose(); p.translate(Mth.lerp(t,-.5,0),.19+Math.sin(t*Math.PI)*.16,Mth.lerp(t,-.5,-.06));
                shell(p,b,light,-1); p.popPose();
            }
        }
    }

    private void action(DevilRouletteTableEntity table,RouletteView v,float age,PoseStack p,MultiBufferSource b,int light,int overlay) {
        if (v.phase()!=RoulettePhase.ITEM && v.phase()!=RoulettePhase.ADRENALINE) return;
        float actor=v.actor().equals(v.front())?-1:1;
        RouletteItem kind=v.item();
        if (v.phase()==RoulettePhase.ADRENALINE) {
            if (age<14) {
                p.pushPose(); p.translate(.3,.24+ease(age/6)*.25,actor*(.35+ease(age/8)*.4));
                p.mulPose(Axis.ZP.rotationDegrees(-55));
                drawItem(table,RouletteItem.ADRENALINE,.45F,p,b,light,overlay);
                box(p,b,light,RED,-.018,.13-ease((age-7)/5)*.10,0,.036,.14,.036);
                p.popPose();
            }
            kind=v.stolen();
            if (kind==null) return;
            if (age<20) {
                if (age<12) return;
                float t=ease((age-12)/8);
                p.pushPose(); p.translate(.35,.25+Math.sin(t*Math.PI)*.25,actor*Mth.lerp(t,-.8,.6));
                p.mulPose(Axis.YP.rotationDegrees(t*180)); drawItem(table,kind,.4F,p,b,light,overlay); p.popPose();
                return;
            }
            age-=15; // 被夺道具仍以同一套独特动作在第 28 tick 生效。
        }
        if (kind==null) return;
        float lift=ease(age/5)*(1-ease((age-22)/4));
        double x=.25,y=.12+lift*.35,z=actor*.55;
        float rx=0,rz=0,ry=actor<0?180:0;
        switch (kind) {
            case MAGNIFYING_GLASS -> { x=.03+Math.sin(age*.18)*.09; y=.30+lift*.12; z=actor*.15; rx=-35; }
            case BEER -> { z=actor*.8; y=.18+lift*.5; rx=actor*65*(float)Math.sin(Mth.clamp((age-5)/14,0,1)*Math.PI); }
            case CIGARETTE -> { z=actor*.83; y=.15+lift*.55; rz=75; }
            case HANDSAW -> { x=Math.sin(age*1.5)*.12; y=.22; z=actor*-.35; rz=35; }
            case HANDCUFFS -> { x=0; z=actor*Mth.lerp(ease(age/13),.6,-.85); y=.14+Math.sin(Mth.clamp(age/26,0,1)*Math.PI)*.3; rz=age*8; }
            case INVERTER -> { x=0; z=0; y=.25+lift*.15; ry+=age*24; rz=25; }
            case TELEPHONE -> { x=-.35; y=.11; z=actor*.55; rz=age<7?(float)Math.sin(age*3)*8:0; }
            case EXPIRED_MEDICINE -> { z=actor*.8; y=.12+lift*.5; rz=age<9?(float)Math.sin(age*3)*18:0; rx=age>=9?actor*75*(float)Math.sin(Mth.clamp((age-9)/12,0,1)*Math.PI):0; }
            case ADRENALINE -> { rz=-50; z=actor*.8; }
        }
        p.pushPose(); p.translate(x,y,z); p.mulPose(Axis.YP.rotationDegrees(ry));
        p.mulPose(Axis.XP.rotationDegrees(rx)); p.mulPose(Axis.ZP.rotationDegrees(rz));
        drawItem(table,kind,.44F,p,b,light,overlay);
        if (kind==RouletteItem.CIGARETTE && age>=10) box(p,b,LightTexture.FULL_BRIGHT,0xFFFF7A36,-.018,.17,-.017,.036,.035,.034);
        if (kind==RouletteItem.TELEPHONE) {
            p.pushPose(); p.translate(0,ease((age-5)/6)*.32,0); p.mulPose(Axis.ZP.rotationDegrees((float)Math.sin(age*.5)*12));
            box(p,b,light,DARK,-.13,.08,-.025,.26,.035,.05);
            box(p,b,light,METAL,-.15,.04,-.045,.07,.07,.09);
            box(p,b,light,METAL,.08,.04,-.045,.07,.07,.09); p.popPose();
        }
        p.popPose();
        if (kind==RouletteItem.HANDSAW && v.saw() && age>=13 && age<22) {
            p.pushPose(); p.translate(.18+(age-13)*.03,.15,-actor*.48); p.mulPose(Axis.XP.rotationDegrees((age-13)*15));
            box(p,b,light,METAL,-.035,0,-.13,.07,.07,.26); p.popPose();
        }
    }

    private void drawItem(DevilRouletteTableEntity table,RouletteItem kind,float scale,PoseStack p,MultiBufferSource b,int light,int overlay) {
        if (kind==null) return;
        p.pushPose(); p.scale(scale,scale,scale);
        // 此重载内部以 null LivingEntity 渲染，专用道具不依赖持有者。
        Minecraft.getInstance().getItemRenderer().renderStatic(stacks.computeIfAbsent(kind,RouletteItems::stack),
                ItemDisplayContext.FIXED,light,overlay,p,b,table.getLevel(),kind.ordinal());
        p.popPose();
    }

    private static void text(PoseStack p,MultiBufferSource b,Component text,double x,double y,double z,float yaw,int color,float scale) {
        Font font=Minecraft.getInstance().font;
        p.pushPose(); p.translate(x,y,z); p.mulPose(Axis.YP.rotationDegrees(yaw)); p.mulPose(Axis.XP.rotationDegrees(-90));
        p.scale(scale,-scale,scale);
        float fitted=Math.min(1,240F/Math.max(1,font.width(text)));
        p.scale(fitted,fitted,fitted);
        font.drawInBatch(text,-font.width(text)/2F,0,color,false,p.last().pose(),b,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        p.popPose();
    }

    private static double slotX(int slot) { return (slot%4-1.5)*.56; }
    private static double slotZ(int slot) { return .61+(slot/4)*.29; }
    private static float ease(float t) { t=Mth.clamp(t,0,1); return t*t*(3-2*t); }
}
