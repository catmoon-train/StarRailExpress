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
        assertFalse(s.unlimitedStamina); assertFalse(s.speedBoost);
        assertFalse(s.noCooldown);
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
        s.unlimitedStamina = true; s.speedBoost = true;
        s.noCooldown = true;
        s.overrides.put(2, new TrainingSettings.BotSettings("starrailexpress:civilian", "combat", "gun"));
        var gson = new Gson();
        String json = gson.toJson(s);
        var copy = gson.fromJson(json, TrainingSettings.class); copy.normalize();
        assertEquals(12, copy.enemyCount); assertEquals("train", copy.area);
        assertEquals("combat", copy.bot(2).behavior); assertEquals("gun", copy.bot(2).equipment);
        assertTrue(copy.unlimitedStamina); assertTrue(copy.speedBoost);
        assertTrue(copy.noCooldown);
        assertFalse(json.contains("pending")); assertFalse(json.contains("uuid"));
        assertFalse(json.contains("alive"));
    }
    @Test void oldOrNullSettingsNormalizeIdempotently() {
        var gson = new Gson();
        var s = gson.fromJson("{\"enemyCount\":0,\"overrides\":null}", TrainingSettings.class);
        s.normalize(); String first = gson.toJson(s); s.normalize();
        assertEquals(first, gson.toJson(s)); assertEquals(0, s.enemyCount);
        assertFalse(s.unlimitedStamina); assertFalse(s.speedBoost);
        assertFalse(s.noCooldown);
    }
    @Test void assistanceSwitchesAreIndependentAndSurviveReload() {
        var gson = new Gson();
        var stamina = gson.fromJson("{\"unlimitedStamina\":true}", TrainingSettings.class);
        stamina.normalize();
        assertTrue(stamina.unlimitedStamina); assertFalse(stamina.speedBoost);
        var speed = gson.fromJson("{\"speedBoost\":true}", TrainingSettings.class);
        speed.normalize();
        assertFalse(speed.unlimitedStamina); assertTrue(speed.speedBoost);
        var copy = gson.fromJson(gson.toJson(speed), TrainingSettings.class);
        assertFalse(copy.unlimitedStamina); assertTrue(copy.speedBoost);
    }
    @Test void noCooldownIsIndependentAndPersistsWithoutOtherAssists() {
        var gson = new Gson();
        var settings = gson.fromJson("{\"noCooldown\":true}", TrainingSettings.class);
        settings.normalize();
        assertTrue(settings.noCooldown);
        assertFalse(settings.unlimitedStamina); assertFalse(settings.speedBoost);
        var copy = gson.fromJson(gson.toJson(settings), TrainingSettings.class);
        assertTrue(copy.noCooldown);
        copy.noCooldown = false;
        assertFalse(gson.fromJson(gson.toJson(copy), TrainingSettings.class).noCooldown);
    }
    @Test void modifierSettingsMigrateOldSavesAndPreserveIndependentSlots() {
        var gson = new Gson();
        var old = gson.fromJson("{\"enemyCount\":5,\"overrides\":{\"2\":{\"role\":\"demo:role\",\"behavior\":\"moving\",\"equipment\":\"gun\"}}}", TrainingSettings.class);
        old.normalize();
        assertTrue(old.myModifiers.isEmpty()); assertTrue(old.enemyModifiers.isEmpty()); assertTrue(old.bot(2).modifiers.isEmpty());
        old.myModifiers.add("demo:fast"); old.enemyModifiers.add("demo:small");
        old.overrides.put(1, new TrainingSettings.BotSettings("demo:role", "combat", "gun", java.util.List.of("demo:strong")));
        var copy = gson.fromJson(gson.toJson(old), TrainingSettings.class); copy.normalize();
        assertEquals(java.util.List.of("demo:fast"), copy.myModifiers);
        assertEquals(java.util.List.of("demo:small"), copy.bot(0).modifiers);
        assertEquals(java.util.List.of("demo:strong"), copy.bot(1).modifiers);
        assertEquals("combat", copy.bot(1).behavior); assertEquals("gun", copy.bot(1).equipment);
        assertTrue(copy.bot(2).modifiers.isEmpty());
        copy.bot(0).modifiers.clear();
        assertEquals(java.util.List.of("demo:small"), copy.enemyModifiers, "继承配置必须返回独立列表");
        copy.myModifiers.clear();
        assertEquals(java.util.List.of("demo:fast"), old.myModifiers);
    }

    @Test void nullModifierFieldsAndDuplicateIdsNormalizeIdempotently() {
        var gson = new Gson();
        var settings = gson.fromJson("{\"myModifiers\":null,\"enemyModifiers\":[\"demo:a\",\"demo:a\",null,\"bad id\"],\"overrides\":{\"0\":{\"modifiers\":null}}}", TrainingSettings.class);
        settings.normalize();
        assertTrue(settings.myModifiers.isEmpty()); assertTrue(settings.bot(0).modifiers.isEmpty());
        assertEquals(java.util.List.of("demo:a"), settings.enemyModifiers);
        String first = gson.toJson(settings); settings.normalize(); assertEquals(first, gson.toJson(settings));
    }
}
