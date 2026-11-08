package io.wifi.starrailexpress.settlement.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static io.wifi.starrailexpress.settlement.client.SettlementPresentation.*;

/** 名片连续帧回归：同镜头固定槽位，不受事件优先级、动作和头部抖动影响。 */
class SettlementNameplateLayoutTest {
    private static final UUID ID=new UUID(0,1),OTHER=new UUID(0,2);
    private static final Rect SCREEN=new Rect(10,30,620,300);

    @Test void aCardNeverChangesSlotsInsideOneShot() {
        var layout=new SettlementNameplateLayout();layout.begin("cast",640,360);
        Rect initial=layout.place(ID,355,180,120,27,SCREEN,List.of(new Rect(280,75,74,90)),8,true);
        assertNotNull(initial);
        for(int frame=0;frame<500;frame++) {
            layout.begin("cast",640,360);
            var movingFace=new Rect(280+Math.sin(frame*.1)*20,75+frame%5,74,90);
            var next=layout.place(ID,320+Math.sin(frame)*70,180+Math.cos(frame)*30,120,27,SCREEN,List.of(movingFace),8,true);
            assertSame(initial,next,"动作和投影锚点不能触发换边或换行");
        }
    }

    @Test void newlyActiveActorsCannotMoveExistingCards() {
        var layout=new SettlementNameplateLayout();layout.begin("replay/mvp",640,360);
        Rect a=layout.place(ID,350,170,120,27,SCREEN,List.of(),8,true);
        Rect b=layout.place(OTHER,350,170,120,27,SCREEN,List.of(),8,true);
        assertNotNull(a);assertNotNull(b);assertFalse(a.overlaps(b));
        // 即使下一帧调用顺序倒置，也不能把先前人物挤走。
        assertSame(b,layout.place(OTHER,100,50,120,27,SCREEN,List.of(),8,true));
        assertSame(a,layout.place(ID,100,50,120,27,SCREEN,List.of(),8,true));
    }

    @Test void cameraCutViewportChangeAndSessionClearAllowNewLayout() {
        var layout=new SettlementNameplateLayout();layout.begin("cast",640,360);
        Rect a=layout.place(ID,450,120,120,27,SCREEN,List.of(),8,true);
        layout.begin("replay/mvp",640,360);
        Rect b=layout.place(ID,100,200,120,27,SCREEN,List.of(),8,true);
        assertNotEquals(a,b);
        layout.begin("replay/mvp",800,450);
        Rect c=layout.place(ID,300,200,120,27,SCREEN,List.of(),8,true);
        assertNotEquals(b,c);
        layout.clear();layout.begin("replay/mvp",800,450);
        assertNotEquals(c,layout.place(ID,100,200,120,27,SCREEN,List.of(),8,true));
    }

    @Test void anUnplaceableCardDoesNotKeepPoppingInOnLaterFrames() {
        var layout=new SettlementNameplateLayout();layout.begin("cast",640,360);
        assertNull(layout.place(ID,300,150,120,27,SCREEN,List.of(SCREEN),8,true));
        assertNull(layout.place(ID,300,150,120,27,SCREEN,List.of(),8,true));
        layout.begin("next",640,360);
        assertNotNull(layout.place(ID,300,150,120,27,SCREEN,List.of(),8,true));
    }
}
