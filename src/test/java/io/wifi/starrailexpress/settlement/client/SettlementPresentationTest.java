package io.wifi.starrailexpress.settlement.client;

import io.wifi.starrailexpress.settlement.SettlementChoreography;
import io.wifi.starrailexpress.settlement.SettlementSnapshot;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static io.wifi.starrailexpress.settlement.client.SettlementPresentation.*;

/** 漫画分镜回归：语义分类、安全构图、名签避让和固定字号的长正文滚动。 */
class SettlementPresentationTest {
    private static final UUID MVP=new UUID(0,1), OTHER=new UUID(0,2);
    private static SettlementSnapshot snapshot(List<SettlementSnapshot.Event> events) {
        return new SettlementSnapshot("minecraft:overworld",0,0,0,"{}",MVP,List.of(
                new SettlementSnapshot.Actor(MVP,"MVP","",0,true,false,false,4,3),
                new SettlementSnapshot.Actor(OTHER,"other","",0,true,false,false,1,2)),events);
    }
    private static SettlementSnapshot.Event event(String type) {return new SettlementSnapshot.Event(5000,type,MVP,OTHER,"{}",null,null,null);}

    @Test void purchasesAndUnknownEventsNeverBecomeAttacks() {
        assertEquals(Action.PURCHASE,action("STORE_BUY"));
        assertEquals(Action.INTERACT,action("TASK_COMPLETE"));
        assertEquals(Action.STILL,action("CUSTOM_EVENT"));
        assertEquals(Action.STILL,action(null));
        assertEquals(Action.STILL,action("new_extension_event"));
        assertEquals(Category.CONFLICT,category("PLAYER_KILL"));
        assertEquals(Action.THROW,action("GRENADE_THROWN"));
        assertEquals(Action.CAST,action("SKILL_RELEASE"));
        assertEquals(Action.TRANSFORM,action("PLAYER_REVIVAL"));
    }

    @Test void dailyEventsKeepOneSteadyCameraAndHaveNoImpact() {
        var snapshot=snapshot(List.of(event("STORE_BUY"),event("TASK_COMPLETE"),event("STORE_BUY")));
        var cues=cues(snapshot);
        assertTrue(cues.isEmpty());
        var a=frame(snapshot,cues,326);var b=frame(snapshot,cues,350);
        assertEquals(a.detail(),b.detail());assertEquals(a.orbit(),b.orbit());
        assertEquals(0,a.impact());assertEquals(0,b.impact());
    }

    @Test void denseEventsKeepFullTimelineButLimitCameraCuts() {
        List<SettlementSnapshot.Event> events=new ArrayList<>();
        for(int i=0;i<2000;i++) events.add(event(i%2==0?"PLAYER_KILL":"SKILL_RELEASE"));
        var snapshot=snapshot(events);var cues=cues(snapshot);
        assertEquals(2000,snapshot.events().size());
        for(int i=1;i<cues.size();i++) assertTrue(cues.get(i).tick()-cues.get(i-1).tick()>=40);
        var f=frame(snapshot,cues,cues.get(1).tick()+9);
        assertTrue(f.impact()>0);assertEquals(Category.CONFLICT,f.category());
        assertTrue(Math.abs(f.roll())<Math.toRadians(2.2));
    }

    @Test void closingPullsBackAndRemovesImpact() {
        var snapshot=snapshot(List.of(event("PLAYER_KILL")));
        var f=frame(snapshot,cues(snapshot),snapshot.durationTicks()-35);
        assertTrue(f.closing());assertEquals(0,f.detail(),1e-9);assertEquals(0,f.impact());
    }

    @Test void captionsFitTheirContentAndNeverCoverTheSafeArea() {
        for(int[] size:new int[][]{{320,180},{480,360},{640,360},{854,480},{1280,540},{360,480}}) {
            var one=layout(size[0],size[1],false,true,1);
            var longText=layout(size[0],size[1],false,true,40);
            assertTrue(one.caption().h()<=size[1]*.15,"短事件应是紧凑条");
            assertTrue(longText.caption().h()<=size[1]*.20+1e-6);
            assertEquals(one.ui(),longText.ui(),"长正文不能无限缩小字号");
            assertFalse(one.safe().overlaps(one.caption()));assertFalse(longText.safe().overlaps(longText.caption()));
        }
    }

    @Test void mvpCardMovesBelowOnNarrowWindows() {
        var wide=layout(854,480,true,false,1);var narrow=layout(360,480,true,false,1);
        assertFalse(wide.stacked());assertTrue(narrow.stacked());
        assertFalse(wide.safe().overlaps(wide.data()));assertFalse(narrow.safe().overlaps(narrow.data()));
        assertTrue(narrow.data().y()>narrow.safe().bottom());
    }

    @Test void cameraFitsAllBoundingCornersIncludingCrownAndProps() {
        for(int count:new int[]{1,2,8,24,32}) for(int[] size:new int[][]{{320,180},{480,360},{854,480},{1280,540},{360,480}}) {
            Bounds all=null;
            for(int i=0;i<count;i++) {
                var slot=SettlementChoreography.winner(i,count);
                var b=new Bounds(slot.x()-.82,0,slot.z()-.65,slot.x()+.82,2.5,slot.z()+.65);
                all=all==null?b:all.union(b);
            }
            for(boolean hero:new boolean[]{false,true}) {
                var l=layout(size[0],size[1],hero,!hero,2);
                Bounds b=hero?new Bounds(-.82,0,-1,.82,2.5,.3):all.union(new Bounds(-1.5,0,4.5,1.5,3.6,7.5));
                var f=new Frame(250,hero?1:0,hero?1:0,0,.045,-.008,MVP,null,Category.STORY,false);
                Camera camera=camera(b,l,f);
                Matrix4d transform=new Matrix4d().perspective(Math.toRadians(camera.fov()),size[0]/(double)size[1],.05,100)
                        .rotateZ(camera.roll()).lookAt(camera.eye(),camera.look(),new org.joml.Vector3d(0,1,0));
                for(var corner:b.corners()) {
                    Vector4d v=new Vector4d(corner,1).mul(transform);
                    double x=(v.x/v.w*.5+.5)*size[0],y=(.5-v.y/v.w*.5)*size[1];
                    assertTrue(x>=l.safe().x()&&x<=l.safe().right()&&y>=l.safe().y()&&y<=l.safe().bottom(),
                            "人物被UI安全区裁切："+count+"人 "+size[0]+"x"+size[1]+" hero="+hero+" point="+x+","+y);
                }
                assertTrue(camera.eye().z>-14,"机位必须在黑箱内");
            }
        }
    }

    @Test void labelsAvoidFacesCrownsCaptionsAndEachOther() {
        Rect screen=new Rect(10,30,620,300);
        List<Rect> occupied=new ArrayList<>(List.of(new Rect(280,75,74,90),new Rect(80,280,480,40)));
        for(int i=0;i<4;i++) {
            Rect label=placeLabel(365,175,140,34,screen,occupied,8,true);
            assertNotNull(label,"侧签应找到空位");assertTrue(screen.contains(label));
            for(Rect obstacle:occupied) assertFalse(label.overlaps(obstacle));
            occupied.add(label.grow(3));
        }
        assertNull(placeLabel(60,60,800,40,screen,occupied,8,true),"过长名签不能越出窗口");
    }

    @Test void longCaptionsScrollToLastLineWithoutShrinking() {
        assertEquals(0,scroll(0,30,220,40));
        assertEquals(180,scroll(29,30,220,40),1e-6);
        assertEquals(0,scroll(20,30,20,40));
        double last=0;
        for(int tick=0;tick<30;tick++) {double now=scroll(tick,30,220,40);assertTrue(now>=last);last=now;}
    }

    @Test void heroPosterIsIndependentOfCastAndTextNeverOverlapsPortrait() {
        for(int[] size:new int[][]{{320,180},{480,360},{854,480},{1280,540},{360,480}}) {
            var hero=heroLayout(size[0],size[1]);
            var screen=new Rect(0,0,size[0],size[1]);
            assertTrue(screen.contains(hero.info()));assertTrue(screen.contains(hero.portrait()));
            assertFalse(hero.info().overlaps(hero.portrait()));
            assertTrue(hero.portrait().h()>=size[1]*.60,"MVP 人像必须有足够画面占比");
        }
    }

    @Test void heroEntranceHoldsItsPoseAndThenLeavesCleanly() {
        var snapshot=snapshot(List.of(event("STORE_BUY")));
        assertEquals(0,frame(snapshot,List.of(),202).hero());
        assertEquals(1,frame(snapshot,List.of(),220).hero());
        assertEquals(1,frame(snapshot,List.of(),280).hero());
        assertEquals(0,frame(snapshot,List.of(),320).hero());
    }

    @Test void conflictMotionAnticipatesHitsHoldsAndRecovers() {
        assertEquals(0,motion(Action.STRIKE,0).hit());
        assertTrue(motion(Action.STRIKE,5).windup()>.9);
        assertEquals(0,motion(Action.STRIKE,5).hit());
        assertTrue(motion(Action.STRIKE,9).hit()>.99);
        assertTrue(motion(Action.STRIKE,12).hit()>.99,"发力后应有定格，而不是马上回摆");
        assertEquals(0,motion(Action.STRIKE,22).hit());
        assertEquals(0,motion(Action.PURCHASE,5).windup(),"购买不应采用攻击蓄势");
    }

    @Test void backdropLayersHaveRealDepthSeparation() {
        assertTrue(SettlementBackdropRenderer.BACK_Z-SettlementBackdropRenderer.FRONT_Z>=.30f,
                "衬板正反层不能继续用近共面透明四边形");
        assertTrue(SettlementBackdropRenderer.FLOOR_Y>=.02f);
    }
    @Test void paperShellIsInFrontOfRealPillarsAndClosesAllEdges() {
        assertTrue(SettlementBackdropRenderer.INNER_X < 13.5f,"±14 立柱内沿是 ±13.5，不能把纸景埋进黑石");
        assertTrue(SettlementBackdropRenderer.INNER_X > 12.8f,"保留原有演员横向空间");
        assertTrue(SettlementBackdropRenderer.NEAR_Z < -13.5f,"纸景必须盖到最远机位后方");
        assertTrue(SettlementBackdropRenderer.NEAR_Z > -14.5f,"前墙覆纸仍在黑箱内部");
        assertTrue(SettlementBackdropRenderer.FAR_Z > 13 && SettlementBackdropRenderer.FAR_Z < 14.5f);
        assertTrue(SettlementBackdropRenderer.CEILING_Y > 12.5f && SettlementBackdropRenderer.CEILING_Y < 13);
    }

    @Test void entranceHeightIsSharedBoundedAndEndsOnTheFloor() {
        for (int order : new int[]{0,1,8,31,511}) {
            double previous=Double.POSITIVE_INFINITY;
            for (int tick=0;tick<=160;tick++) {
                double lift=winnerLift(tick,order);
                assertTrue(lift>=0 && lift<=ENTRANCE_HEIGHT);
                assertTrue(lift<=previous+1e-9);
                var b=winnerBounds(1.35,-.35,order,tick);
                assertEquals(lift,b.y0());assertEquals(lift+2.5,b.y1());
                assertTrue(b.y1()<SettlementBackdropRenderer.CEILING_Y);
                previous=lift;
            }
            assertEquals(0,winnerLift(ENTRANCE_END,order),1e-9);
        }
    }

    @Test void descendingWinnersStayInFrameAndCameraStaysInsidePaperShell() {
        var s=snapshot(List.of());
        for (int count:new int[]{1,2,8,24,32}) for(int[] size:new int[][]{{320,180},{480,360},{854,480},{1280,540},{360,480}})
            for(int tick:new int[]{0,20,26,36,48,56,62,80}) {
                Bounds b=null;
                for(int i=0;i<count;i++) {
                    var slot=SettlementChoreography.winner(i,count);
                    var actor=winnerBounds(slot.x(),slot.z(),i,tick);
                    b=b==null?actor:b.union(actor);
                }
                var l=layout(size[0],size[1],false,false,1);
                var f=frame(s,List.of(),tick);
                var camera=camera(b,l,f);
                assertTrue(Math.abs(camera.eye().x)<SettlementBackdropRenderer.INNER_X,"镜头不能退到纸侧墙外");
                assertTrue(camera.eye().z>SettlementBackdropRenderer.NEAR_Z,"镜头不能退到纸前墙外");
                assertTrue(camera.eye().y>SettlementBackdropRenderer.FLOOR_Y && camera.eye().y<SettlementBackdropRenderer.CEILING_Y,
                        "入场机位穿过纸顶/地面：count="+count+" tick="+tick+" y="+camera.eye().y);
                Matrix4d transform=new Matrix4d().perspective(Math.toRadians(camera.fov()),size[0]/(double)size[1],.05,100)
                        .rotateZ(camera.roll()).lookAt(camera.eye(),camera.look(),new org.joml.Vector3d(0,1,0));
                for(var corner:b.corners()) {
                    Vector4d v=new Vector4d(corner,1).mul(transform);
                    double x=(v.x/v.w*.5+.5)*size[0],y=(.5-v.y/v.w*.5)*size[1];
                    assertTrue(x>=l.safe().x()&&x<=l.safe().right()&&y>=l.safe().y()&&y<=l.safe().bottom(),
                            "下降中的人物被裁切：count="+count+" tick="+tick+" point="+x+","+y);
                }
            }
    }
    @Test void enlargedHeroCopyHasIndependentRowsOnWideAndNarrowWindows() {
        for(int[] size:new int[][]{{320,180},{480,360},{640,360},{854,480},{1280,540},{360,480},{1920,1080}}) {
            var poster=heroLayout(size[0],size[1]);
            float ui=layout(size[0],size[1],true,false,1).ui();
            var copy=heroText(poster,ui);
            var rows=List.of(copy.award(),copy.name(),copy.role(),copy.stats(),copy.statLabels(),copy.victory());
            for(int i=0;i<rows.size();i++) {
                assertTrue(poster.info().grow(.001).contains(rows.get(i)),"文字轨道越出信息区");
                assertFalse(rows.get(i).overlaps(poster.portrait()),"放大文字不能盖住 MVP 人像");
                for(int j=i+1;j<rows.size();j++) assertFalse(rows.get(i).overlaps(rows.get(j)),"职业/姓名/统计/胜负不能重叠");
            }
            assertTrue(copy.roleScale()>=1.19f*ui,"窄屏职业名也必须比旧 .9 倍明显放大");
            assertTrue(copy.victoryScale()>=1.34f*ui,"获胜文案不再使用旧 .82 倍小字");
            assertTrue(copy.roleScale()*9<=copy.role().h()+.001);
            assertTrue(copy.victoryScale()*9<=copy.victory().h()+.001);
            assertTrue(copy.victory().w()>0&&copy.victory().h()>0,"窄屏不能隐藏阵营获胜文字");
        }
    }

    @Test void wideHeroRoleAndVictoryPreferReadableLargerType() {
        var poster=heroLayout(854,480);
        float ui=layout(854,480,true,false,1).ui();
        var copy=heroText(poster,ui);
        assertEquals(1.55f*ui,copy.roleScale(),.001);
        assertEquals(1.55f*ui,copy.victoryScale(),.001);
        assertTrue(copy.victory().y()>copy.statLabels().bottom());
    }

    @Test void closeupScreenIsLowWideAndCompletelyRetractsForGroupShots() {
        assertTrue(SettlementBackdropRenderer.SCREEN_HEIGHT<4,"近景屏风不能再高过整面画格");
        assertTrue(SettlementBackdropRenderer.SCREEN_HALF_WIDTH<SettlementBackdropRenderer.INNER_X);
        assertTrue(SettlementBackdropRenderer.SCREEN_HALF_WIDTH>12.7,"横向窗景覆盖整排人物而非孤立的中央大白板");
        assertTrue(backdropRise(0)+SettlementBackdropRenderer.SCREEN_HEIGHT<0);
        assertEquals(0,backdropRise(1),1e-9);
        double previous=Double.NEGATIVE_INFINITY;
        for(int i=0;i<=100;i++) {
            double rise=backdropRise(i/100.0);
            assertTrue(rise>=previous);assertTrue(rise<=0);
            previous=rise;
        }
        assertTrue(SettlementBackdropRenderer.BACK_Z-SettlementBackdropRenderer.FRONT_Z>=.30f);
    }

    @Test void identitiesHaveHardLimitsEvenWithHundredsOfRoles() {
        for(int count:new int[]{1,2,8,24,32,128,512}) {
            List<SettlementSnapshot.Actor> actors=new ArrayList<>();
            for(int i=1;i<=count;i++) actors.add(new SettlementSnapshot.Actor(new UUID(0,i),
                    "very_long_player_name_"+i,"custom:very_long_role_name_"+i,0x448866,true,false,false,0,0));
            var snapshot=new SettlementSnapshot("minecraft:overworld",0,0,0,"{}",MVP,actors,List.of(event("TASK_COMPLETE")));
            var event=new SettlementSnapshot.Event(1000,"PLAYER_KILL",actors.get(Math.min(1,count-1)).id(),
                    actors.get(Math.min(2,count-1)).id(),"{}",null,null,null);
            var frame=frame(snapshot,List.of(),330);
            var identities=identityActors(snapshot,frame,event);var names=nameActors(snapshot,frame,event);
            assertTrue(identities.size()<=2,"职业只显示当前事件两人");
            assertTrue(names.size()<=MAX_NAME_MARKERS,"姓名不能随总人数铺满场景");
            assertEquals(identities.size(),new java.util.HashSet<>(identities).size());
            assertEquals(names.size(),new java.util.HashSet<>(names).size());
            assertTrue(names.contains(MVP));
            assertEquals(List.of(MVP),identityActors(snapshot,frame,null),"全景只保留 MVP 焦点信息");
            assertTrue(nameActors(snapshot,frame(snapshot,List.of(),250),event).isEmpty(),"海报段不叠加姓名条");
        }
    }

    @Test void identityFallbackHandlesUnknownAndNonWinningParticipants() {
        UUID loser=new UUID(0,3),unknown=new UUID(0,999);
        var snapshot=new SettlementSnapshot("minecraft:overworld",0,0,0,"{}",MVP,List.of(
                new SettlementSnapshot.Actor(MVP,"mvp","a:b",0,true,false,false,0,0),
                new SettlementSnapshot.Actor(loser,"loser","a:c",0,false,true,true,0,0)),List.of(event("GAME_START")));
        var frame=frame(snapshot,List.of(),330);
        var event=new SettlementSnapshot.Event(0,"PLAYER_KILL",loser,MVP,"{}",null,null,null);
        assertEquals(List.of(loser,MVP),identityActors(snapshot,frame,event),"职业栏保留实际事件双方身份");
        assertEquals(List.of(MVP),nameActors(snapshot,frame,event),"后台死者不叠加场上姓名");
        assertEquals(List.of(MVP),identityActors(snapshot,frame,new SettlementSnapshot.Event(0,"UNKNOWN",unknown,unknown,"{}",null,null,null)));
    }

    @Test void identityFootprintIsBoundedAndOutsideTheActorSafeArea() {
        for(int[] size:new int[][]{{320,180},{480,360},{640,360},{854,480},{1280,540},{360,480},{1920,1080}})
            for(boolean replay:new boolean[]{false,true}) for(int lines:new int[]{1,2,40}) {
                var l=layout(size[0],size[1],false,replay,lines);var rail=l.identity();
                assertTrue(new Rect(0,0,size[0],size[1]).contains(rail));
                assertFalse(l.safe().overlaps(rail));assertFalse(rail.overlaps(l.caption()));
                double names=MAX_NAME_MARKERS*NAME_MARKER_WIDTH*NAME_MARKER_HEIGHT*l.ui()*l.ui();
                assertTrue(rail.w()*rail.h()+names<size[0]*size[1]*.10,
                        "职业栏与姓名最大占用须低于画面 10%，不能回到满屏名片");
                if(replay) assertTrue(l.safe().bottom()<rail.y() && rail.bottom()<l.caption().y());
            }
        var cast=layout(854,480,false,false,1);
        assertTrue(cast.safe().y()<480*.20,"紧凑标题不再吃掉大半个上半画面");
    }

    @Test void fastEntranceFinishesWithinThreePointOneSeconds() {
        assertEquals(62,ENTRANCE_END);assertEquals(30,ENTRANCE_TICKS);
        for(int order:new int[]{0,1,8,31,511}) {
            assertEquals(ENTRANCE_HEIGHT,winnerLift(ENTRANCE_START,order),1e-9);
            assertTrue(winnerLift(48,order)<ENTRANCE_HEIGHT*.65);
            assertEquals(0,winnerLift(62,order),1e-9);
            assertEquals(0,winnerLift(120,order),1e-9);
        }
        assertTrue(ENTRANCE_END<SettlementChoreography.DROP_START,"仍先让胜者落地，再安排后台尸体");
    }
}
