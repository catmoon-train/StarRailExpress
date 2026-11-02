package io.wifi.starrailexpress.client.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientPlayerDataCacheTest {
    private final UUID player = UUID.randomUUID();
    private static final String ECONOMY = """
            {"equipped":{"knife":"unique_render_skin"},
             "unlocked":{"knife":{"unique_render_skin":true,"locked":false}},
             "coinNum":42,"lootChance":3,"version":7}
            """;

    @BeforeEach
    @AfterEach
    void clear() {
        ClientPlayerDataCache.clear();
    }

    @Test
    void repeatedRenderLookupsReuseDecodedStrings() {
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 10);
        String first = ClientPlayerDataCache.getEquippedSkin(player, "knife");
        assertEquals("unique_render_skin", first);
        for (int i = 0; i < 10_000; i++) {
            assertSame(first, ClientPlayerDataCache.getEquippedSkin(player, "knife"));
            assertTrue(ClientPlayerDataCache.isSkinUnlocked(player, "knife", first));
        }
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(player, "knife", "locked"));
    }

    @Test
    void acceptedUpdatesReplaceSnapshotButOlderUpdatesDoNot() {
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 10);
        ClientPlayerDataCache.update(player, "economy", "{\"equipped\":{\"knife\":\"older\"}}", 9);
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        ClientPlayerDataCache.update(player, "economy", "{\"equipped\":{\"knife\":\"equal_time\"}}", 10);
        assertEquals("equal_time", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        ClientPlayerDataCache.update(player, "economy", "{}", 11);
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(player, "knife", "unique_render_skin"));
    }

    @Test
    void mutableEconomyCopiesCannotChangeRenderSnapshotOrEachOther() {
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 10);
        var copy = ClientPlayerDataCache.economy(player);
        assertEquals(42, copy.coinNum);
        assertEquals(3, copy.lootChance);
        assertEquals(7, copy.version);
        copy.equipped.put("knife", "local_edit");
        copy.unlocked.get("knife").clear();
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        assertTrue(ClientPlayerDataCache.isSkinUnlocked(player, "knife", "unique_render_skin"));
        var second = ClientPlayerDataCache.economy(player);
        assertNotSame(copy, second);
        assertEquals("unique_render_skin", second.getEquippedSkin("knife"));
        assertTrue(second.isSkinUnlocked("knife", "unique_render_skin"));
    }

    @Test
    void playersAndPartsAreIsolatedAndClearAllowsNewServerTimestamps() {
        UUID other = UUID.randomUUID();
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 100);
        ClientPlayerDataCache.update(other, "economy", "{\"equipped\":{\"knife\":\"other\"}}", 100);
        ClientPlayerDataCache.update(player, "backpack", "{}", 200);
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        assertEquals("other", ClientPlayerDataCache.getEquippedSkin(other, "knife"));
        ClientPlayerDataCache.clear();
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(other, "knife"));
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 1);
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "{", "[]", "{\"equipped\":null,\"unlocked\":null}",
            "{\"equipped\":{\"knife\":null},\"unlocked\":{\"knife\":null}}", "{\"equipped\":[]}"})
    void badOrNullDataFallsBackAndCanRecover(String json) {
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 1);
        ClientPlayerDataCache.update(player, "economy", json, 2);
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(player, "knife", "unique_render_skin"));
        assertTrue(ClientPlayerDataCache.isSkinUnlocked(player, "knife", "default"));
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 3);
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
    }

    @Test
    void missingAndNullQueryInputsAreSafe() {
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(null, "knife"));
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(null, "knife", "missing"));
        assertTrue(ClientPlayerDataCache.isSkinUnlocked(null, null, "default"));
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 1);
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, null));
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, " "));
        assertEquals("default", ClientPlayerDataCache.getEquippedSkin(player, "missing"));
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(player, null, "missing"));
        assertFalse(ClientPlayerDataCache.isSkinUnlocked(player, "knife", null));
    }

    @Test
    void rejectedUpdateInputsLeaveExistingSnapshotUntouched() {
        ClientPlayerDataCache.update(player, "economy", ECONOMY, 1);
        ClientPlayerDataCache.update(null, "economy", "{}", 2);
        ClientPlayerDataCache.update(player, null, "{}", 2);
        ClientPlayerDataCache.update(player, " ", "{}", 2);
        ClientPlayerDataCache.update(player, "economy", null, 2);
        assertEquals("unique_render_skin", ClientPlayerDataCache.getEquippedSkin(player, "knife"));
    }
}
