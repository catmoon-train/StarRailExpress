package org.agmas.noellesroles.game.modes.echo.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 地图接线回归；不启动游戏注册表，界面几何由 EchoUiGeometryCheck 验证。 */
class EchoAreaMapIntegrationTest {
    private String source(String path) throws Exception { return Files.readString(Path.of("src/main/java", path)); }
    @Test void sharedMKeyOpensAreaMapOnlyInEchoAndClosesOnSameBinding() throws Exception {
        var input=source("io/wifi/starrailexpress/client/InputHandler.java");
        var keyBranch=input.substring(input.indexOf("if (openVotingScreenKeybind.consumeClick())"),input.indexOf("if (openClueArchiveKeybind.consumeClick())"));
        assertTrue(keyBranch.contains("EchoFacilityMapClient.inMatch()"));
        assertTrue(keyBranch.contains("new org.agmas.noellesroles.client.screen.AreaMapScreen()"));
        assertTrue(keyBranch.contains("MapVoteScreen.create()"));
        var screen=source("org/agmas/noellesroles/client/screen/AreaMapScreen.java");
        assertTrue(screen.contains("getOpenVotingScreenKeybind().matches(keyCode, scanCode)"));
        assertTrue(screen.contains("if (!EchoFacilityMapClient.inMatch()"));
        assertTrue(screen.contains("minimumZoom()"));
    }
    @Test void hudAndTerrainRemainAvailableWithoutHoldingMapAndUseCachedGraphics() throws Exception {
        var hud=source("org/agmas/noellesroles/client/map/AreaMapHud.java");
        assertTrue(hud.contains("!echo && !AreaMapManager.isHoldingMap(player)"));
        assertTrue(hud.contains("EchoFacilityMapClient.entries()"));
        assertTrue(hud.contains("FakeGuiGraphics g"));
        assertFalse(hud.contains("getDefaultGuiGraphics()"));
        var manager=source("org/agmas/noellesroles/client/map/AreaMapManager.java");
        assertTrue(manager.contains("isHoldingMap(mc.player) || EchoFacilityMapClient.inMatch()"));
        assertTrue(manager.contains("scannedWorld != mc.level"));
        assertTrue(manager.contains("SCAN_BUDGET_PER_TICK = 800"));
    }
    @Test void newMapStringsAndPlaceholdersExistInAllThreeLocales() throws Exception {
        var root=Path.of("src/main/resources/assets/echo_map/lang");
        var chinese=JsonParser.parseString(Files.readString(root.resolve("zh_cn.json"))).getAsJsonObject();
        for(String locale:new String[]{"zh_cn","zh_tw","en_us"}) {
            var json=JsonParser.parseString(Files.readString(root.resolve(locale+".json"))).getAsJsonObject();
            assertEquals(chinese.keySet(),json.keySet(),locale);
            for(var key:chinese.keySet()) {
                assertFalse(json.get(key).getAsString().isBlank(),key);
                assertEquals(chinese.get(key).getAsString().split("%s",-1).length,json.get(key).getAsString().split("%s",-1).length,locale+":"+key);
            }
        }
    }
}
