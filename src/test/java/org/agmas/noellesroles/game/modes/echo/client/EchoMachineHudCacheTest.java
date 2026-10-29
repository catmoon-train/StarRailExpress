package org.agmas.noellesroles.game.modes.echo.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 公共 HUD 只在脏 tick 录制，机器卡片必须参与缓存才能在中间帧持续显示。 */
class EchoMachineHudCacheTest {
    @Test void machineCardNeverBypassesHudFrameCache() throws Exception {
        String source=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/client/EchoMachineClient.java"));
        String card=source.substring(source.indexOf("static int renderHud("));
        assertFalse(card.contains("getDefaultGuiGraphics("));
        assertTrue(card.contains("var g = graphics;"));
        assertTrue(card.contains("fit(FakeGuiGraphics g,"));
        assertTrue(card.contains("g.renderItem("));
        assertTrue(card.contains("g.fillGradient("));
    }
}
