package io.wifi.starrailexpress.training;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 训练控制的接线回归，不替代游戏内尸体交互验收。 */
class TrainingControlIntegrationTest {
    private String session() throws Exception {
        return Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/training/TrainingSession.java"));
    }

    @Test void rangedKitReplacesSheriffRevolverWithSniper() throws Exception {
        String source = session();
        String kit = source.substring(source.indexOf("case \"ranged\""), source.indexOf("case \"utility\""));
        assertTrue(kit.contains("TMMItems.SNIPER_RIFLE.getDefaultInstance()"));
        assertTrue(kit.contains("TMMItems.REVOLVER.getDefaultInstance()"));
        assertTrue(kit.contains("copyWithCount(64)"));
        assertFalse(kit.contains("SHERIFF_REVOLVER"));
    }

    @Test void bodyActionUsesOwnerPermissionAndEmptyBodyApi() throws Exception {
        String source = session();
        assertTrue(source.contains("case \"spawn_body\" -> spawnBody(actor)"));
        int guard = source.indexOf("!actor.getUUID().equals(ownerId)");
        assertTrue(guard >= 0 && guard < source.indexOf("case \"spawn_body\""));
        assertTrue(source.contains("actor.serverLevel() != level()"));
        String spawn = source.substring(source.indexOf("private void spawnBody("), source.indexOf("public void cleanup("));
        assertTrue(spawn.contains("!TrainingRules.isAlive(actor)"));
        assertTrue(spawn.contains(".contains(actor.position())"));
        assertTrue(spawn.contains("entity instanceof PlayerBodyEntity && ++count >= 48"));
        assertTrue(spawn.contains("GameUtils.spawnBodyEntity(actor, null,"));
        assertTrue(spawn.contains("DeathReasons.GENERIC, false,"));
        assertTrue(spawn.contains("actor.position(), actor.getYHeadRot(), 0"));
        assertFalse(spawn.contains("killPlayer("));
        assertFalse(spawn.contains("getInventory()"));
        String screen = Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/training/client/TrainingScreen.java"));
        assertTrue(screen.contains("() -> send(\"spawn_body\")"));
    }

    @Test void allTrainingLanguagesExposeBodyControlsAndSheriffLabel() throws Exception {
        for (String language : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/starrailexpress/lang", language + ".json"))).getAsJsonObject();
            for (String key : new String[]{"ui.spawn_body", "ui.spawn_body_hint", "error.spawn_body", "error.body_limit"})
                assertFalse(json.get("training.sre." + key).getAsString().isBlank());
            assertEquals(switch (language) { case "zh_cn" -> "警长"; case "zh_tw" -> "警長"; default -> "Sheriff"; },
                    json.get("training.sre.ui.team.sheriff").getAsString());
        }
    }
}
