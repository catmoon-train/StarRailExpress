package org.agmas.noellesroles.game.modes.echo.inventory;

import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 拆包不能因客户端序号恢复而重放别的操作留下的 COOLDOWN。 */
class EchoPackRequestSequenceTest {
    @Test void restoredClientDoesNotReplayCachedCooldownForANewPack() {
        var window=new EchoRequestWindow();
        assertEquals("",window.accept(1,window.epoch(),1,0,false));
        window.remember(1,"COOLDOWN");
        assertEquals("DUPLICATE",window.accept(1,window.epoch(),1,1,false));
        assertEquals("COOLDOWN",window.cached(1).orElseThrow());
        long fresh=EchoRequestWindow.nextRequestId(0,1);
        assertEquals(2,fresh);
        assertEquals("",window.accept(1,window.epoch(),fresh,1,false));
        assertTrue(window.cached(fresh).isEmpty());
    }
    @Test void sequenceAlwaysExceedsBothLocalAndAcknowledgedValues() {
        assertEquals(1,EchoRequestWindow.nextRequestId(0,0));
        assertEquals(11,EchoRequestWindow.nextRequestId(10,3));
        assertEquals(16,EchoRequestWindow.nextRequestId(10,15));
        assertThrows(IllegalArgumentException.class,() -> EchoRequestWindow.nextRequestId(-1,0));
        assertThrows(ArithmeticException.class,() -> EchoRequestWindow.nextRequestId(Long.MAX_VALUE,0));
    }
    @Test void freshPacksStartNormallyAndRealCancellationCooldownIsPreserved() {
        for(var faction:EchoFaction.values()) for(var quality:EchoDraftService.PackQuality.values()) {
            UUID player=UUID.randomUUID();
            var pack=new EchoPackDraft(UUID.randomUUID(),quality);
            var context=EchoDraftService.Context.all(faction,true,true);
            assertTrue(pack.begin(player,100,true,context,new Random(1)));
            assertTrue(pack.cancel(player,101));
            assertFalse(pack.begin(player,300,true,context,new Random(2)));
            assertTrue(pack.begin(player,301,true,context,new Random(2)));
            pack.advance(331);
            assertEquals(EchoPackDraft.State.OFFERED,pack.state());
        }
    }
    @Test void bothClientRequestPathsRestoreSequenceAndEpochClearsOldReplies() throws Exception {
        String client=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/client/EchoBagClient.java"));
        assertTrue(client.contains("if(data==null || data.epoch()==null) return;"));
        assertFalse(client.contains("epoch=null; sequence=0"));
        assertEquals(2,client.lines().filter(l -> l.contains("sequence=EchoRequestWindow.nextRequestId(sequence,data.acknowledged())")).count());
        String data=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/state/EchoRoleData.java"));
        String attach=data.substring(data.indexOf("public void attachInventory("),data.indexOf("public void projectInventory("));
        assertTrue(attach.contains("acknowledged=0; requestError=\"\";"));
        assertTrue(attach.contains("!Objects.equals(this.epoch,epoch)"));
    }
}
