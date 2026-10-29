package org.agmas.noellesroles.game.modes.echo.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 心形精灵与文字一样必须缓存，不能在每个 tick 的第一帧之外消失。 */
class EchoHealthHudCacheTest {
    @Test void healthSpritesUseTheCachedGraphics() throws Exception {
        String source=Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo/client/EchoHud.java"));
        String hearts=source.substring(source.indexOf("var health = layout.health();"));
        assertFalse(hearts.contains("getDefaultGuiGraphics("));
        assertTrue(hearts.contains("var graphics = g;"));
        assertTrue(hearts.contains("graphics.blitSprite("));
        assertTrue(hearts.contains("d.health()"));
    }
    @Test void evacuationHudHasNoQuotaDenominator() throws Exception {
        for(String language:new String[]{"zh_cn","zh_tw","en_us"}) {
            var text=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/echo_hud/lang/"+language+".json"))).getAsJsonObject();
            for(String key:new String[]{"evac.open","evac.overload","evac.confirm"})
                assertFalse(text.get("echo.dashboard."+key).getAsString().contains("%s/%s"),language+":"+key);
        }
    }
}
