package io.wifi.starrailexpress.training;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingSettingsTest {
    @Test void defaultsAreRealisticAndBounded() {
        var s = new TrainingSettings();
        s.normalize();
        assertEquals(5, s.enemyCount); assertEquals("stationary", s.behavior);
        assertFalse(s.friendlyFirePenalty); assertFalse(s.paused);
        assertTrue(s.autoRespawn); assertEquals(3, s.respawnDelay);
    }
    @Test void untrustedValuesCannotEscapeLimits() {
        var s = new TrainingSettings();
        s.enemyCount = Integer.MAX_VALUE; s.respawnDelay = -10;
        s.behavior = "arbitrary"; s.equipment = null; s.area = "../../world";
        s.overrides.put(-1, new TrainingSettings.BotSettings());
        s.overrides.put(32, new TrainingSettings.BotSettings());
        s.normalize();
        assertEquals(32, s.enemyCount); assertEquals(1, s.respawnDelay);
        assertEquals("stationary", s.behavior); assertEquals("knife", s.equipment);
        assertEquals("range", s.area); assertTrue(s.overrides.isEmpty());
        s.enemyCount = -1; s.normalize(); assertEquals(0, s.enemyCount);
    }
    @Test void settingsRoundTripButNoOnlineStateIsPersisted() {
        var s = new TrainingSettings();
        s.enemyCount = 12; s.area = "train";
        s.overrides.put(2, new TrainingSettings.BotSettings("starrailexpress:civilian", "combat", "gun"));
        var gson = new Gson();
        String json = gson.toJson(s);
        var copy = gson.fromJson(json, TrainingSettings.class); copy.normalize();
        assertEquals(12, copy.enemyCount); assertEquals("train", copy.area);
        assertEquals("combat", copy.bot(2).behavior); assertEquals("gun", copy.bot(2).equipment);
        assertFalse(json.contains("pending")); assertFalse(json.contains("uuid"));
        assertFalse(json.contains("alive"));
    }
    @Test void oldOrNullSettingsNormalizeIdempotently() {
        var gson = new Gson();
        var s = gson.fromJson("{\"enemyCount\":0,\"overrides\":null}", TrainingSettings.class);
        s.normalize(); String first = gson.toJson(s); s.normalize();
        assertEquals(first, gson.toJson(s)); assertEquals(0, s.enemyCount);
    }
}
