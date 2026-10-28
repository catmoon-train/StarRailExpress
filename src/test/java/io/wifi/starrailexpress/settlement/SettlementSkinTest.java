package io.wifi.starrailexpress.settlement;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 皮肤随冻结快照同步，旧快照和未知皮肤回落到默认舞台。 */
class SettlementSkinTest {
    private SettlementSnapshot snapshot(String skin) {
        return new SettlementSnapshot("minecraft:overworld", -100000, 0, 0, "{}", UUID.randomUUID(),
                List.of(), List.of(), skin);
    }

    @Test void dragonThemeSurvivesNetworkSerialization() {
        Gson gson = new Gson();
        var original = snapshot("ender_dragon");
        var decoded = gson.fromJson(gson.toJson(original), SettlementSnapshot.class);
        assertTrue(decoded.enderDragon());
        assertEquals(original.mvp(), decoded.mvp());
        assertEquals(original.durationTicks(), decoded.durationTicks());
    }

    @Test void oldAndUnknownSkinsKeepDefaultStage() {
        assertFalse(snapshot(null).enderDragon());
        assertFalse(snapshot("unknown").enderDragon());
        assertFalse(snapshot("default").enderDragon());
        var old = new SettlementSnapshot("minecraft:overworld", 0, 0, 0, "{}", null, List.of(), List.of());
        assertFalse(old.enderDragon());
    }
}
