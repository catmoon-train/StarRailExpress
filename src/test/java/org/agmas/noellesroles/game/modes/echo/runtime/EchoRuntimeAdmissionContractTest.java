package org.agmas.noellesroles.game.modes.echo.runtime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 只读接线回归，不加载Minecraft注册表；真实碰撞与服务器事件仍须集成验收。 */
class EchoRuntimeAdmissionContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles/game/modes/echo",relative),StandardCharsets.UTF_8);
    }
    @Test void productionPreflightHasNoDevelopmentPropertyGateAndKeepsSafetyChecks() throws Exception {
        var preflight=source("runtime/EchoPreflight.java");
        assertFalse(preflight.contains("Boolean.getBoolean"));
        assertFalse(preflight.contains("echo.error.development_only"));
        assertTrue(preflight.contains("getCollisionShape"));
        assertTrue(preflight.contains("isWithinBounds"));
        assertTrue(preflight.contains("hasChunkAt"));
        assertTrue(preflight.contains("EchoMapValidator.validate"));
        var mode=source("EchoGameMode.java");
        assertTrue(mode.contains("MISSING_SESSION"));
        assertTrue(mode.contains("GameUtils.stopGame(level)"));
        // 开发开关只保护作弊命令，不能阻止正常游戏。
        assertTrue(source("command/EchoCommand.java").contains("literal(\"debug\").requires"));
    }
    @Test void loadingPrecedesSafetyAndStampAndEarlyGateOnlyInspectsConfiguration() throws Exception {
        var admission=source("runtime/EchoRouteAdmission.java");
        int loading=admission.indexOf("job.chunks.advance(tick)");
        int safety=admission.indexOf("var safety=EchoPreflight.check");
        int stamp=admission.indexOf("job.permit=new EchoRoutePermit(stamp");
        assertTrue(loading>=0 && loading<safety && safety<stamp);
        assertTrue(source("EchoBootstrap.java").contains("EchoPreflight.configuration(level, players.size())"));
        assertTrue(admission.contains("EchoSessions.holdChunks(level,job.chunks)"));
        assertTrue(admission.contains("finally { discard(level); }"));
        assertTrue(source("runtime/EchoSessions.java").contains("finally { releaseChunks(level); DEBUG.remove(level); }"));
    }
    @Test void unusedLegacyBrokerRegionsAreNotInspected() throws Exception {
        assertFalse(source("runtime/EchoPreflight.java").contains("map.brokerSpawns()"));
        assertFalse(source("map/EchoRouteCheck.java").contains("map.brokerSpawns()"));
    }
}
