package org.agmas.noellesroles.game.modes.echo.state;

import com.google.gson.JsonParser;
import org.agmas.noellesroles.game.modes.echo.anim.EchoRollAnim;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 只测会话时钟、取消不退款与动画边界，不加载游戏注册表。 */
class EchoRollStateTest {
    @Test void eightTickRollHasThirtySecondCooldownAndCannotStack() {
        var roll=new EchoRollState(); assertTrue(roll.start(100));
        assertEquals(700,roll.readyAt()); assertEquals(108,roll.until());
        assertTrue(roll.active(107)); assertFalse(roll.active(108));
        assertFalse(roll.start(101)); assertFalse(roll.start(699)); assertTrue(roll.start(700));
    }
    @Test void pauseAndReconnectDoNotRefundCooldownAndCancelOnlyEndsMotion() {
        var roll=new EchoRollState(); roll.start(50);
        for(int i=0;i<100;i++) { assertFalse(roll.ready(50)); assertTrue(roll.active(50)); }
        roll.cancel(); assertFalse(roll.active(50)); assertEquals(650,roll.readyAt());
        assertFalse(roll.start(649)); assertTrue(roll.start(650));
        roll.clear(); assertTrue(roll.ready(0)); assertFalse(roll.active(-1)); assertFalse(roll.start(-1));
    }
    @Test void malformedDirectionsCannotCarryArbitraryVelocity() {
        assertTrue(EchoRollState.validDirection(1,0)); assertTrue(EchoRollState.validDirection(.7071,.7071));
        assertFalse(EchoRollState.validDirection(0,0)); assertFalse(EchoRollState.validDirection(10,0));
        assertFalse(EchoRollState.validDirection(1,1)); assertFalse(EchoRollState.validDirection(Double.NaN,1));
        assertFalse(EchoRollState.validDirection(0,Double.POSITIVE_INFINITY));
    }
    @Test void animationMakesOneTurnAndReturnsToNeutralWithoutNonFinitePoses() {
        assertEquals(0,EchoRollAnim.turn(0)); assertEquals(360,EchoRollAnim.turn(1));
        assertEquals(0,EchoRollAnim.curl(0),1e-5); assertEquals(0,EchoRollAnim.curl(1),1e-5);
        assertEquals(1,EchoRollAnim.curl(.5F),1e-5);
        float previous=0;
        for(int i=0;i<=100;i++) { float angle=EchoRollAnim.turn(i/100F); assertTrue(angle>=previous); previous=angle; }
        assertEquals(0,EchoRollAnim.turn(Float.NaN));
    }
    @Test void threeLanguagesAndMixinRegistrationsStayComplete() throws Exception {
        Set<String> keys=null;
        for(String language:new String[]{"zh_cn","zh_tw","en_us"}) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/echo_roll/lang/"+language+".json"))).getAsJsonObject();
            if(keys==null) keys=Set.copyOf(json.keySet()); else assertEquals(keys,json.keySet());
            assertTrue(json.has("echo.roll.hud")); assertTrue(json.has("echo.match.started"));
        }
        var mixins=JsonParser.parseString(Files.readString(Path.of("src/main/resources/noellesroles.mixins.json"))).getAsJsonObject().getAsJsonArray("client").toString();
        for(String name:new String[]{"EchoRollPlayerMixin","EchoRollModelMixin","EchoRollInputMixin","EchoRollHandMixin"}) assertTrue(mixins.contains(name),name);
    }
}
