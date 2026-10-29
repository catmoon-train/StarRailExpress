package io.wifi.starrailexpress.settlement;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 皮肤随冻结快照同步；旧快照和未知皮肤回落到默认舞台。
 * <p>
 * 本体不再认识任何具体皮肤 ID，这里只验证「默认 / 非默认」的通用语义。
 */
class SettlementSkinTest {
    private SettlementSnapshot snapshot(String skin) {
        return new SettlementSnapshot("minecraft:overworld", -100000, 0, 0, "{}", UUID.randomUUID(),
                List.of(), List.of(), skin);
    }

    @Test void skinIdSurvivesNetworkSerialization() {
        Gson gson = new Gson();
        var original = snapshot("some_skin");
        var decoded = gson.fromJson(gson.toJson(original), SettlementSnapshot.class);
        assertEquals("some_skin", decoded.mvpSkin());
        assertTrue(decoded.hasSkin());
        assertEquals(original.mvp(), decoded.mvp());
        assertEquals(original.durationTicks(), decoded.durationTicks());
    }

    @Test void oldAndUnknownSkinsKeepDefaultStage() {
        assertFalse(snapshot(null).hasSkin());
        assertFalse(snapshot("").hasSkin());
        assertFalse(snapshot(SettlementSnapshot.DEFAULT_SKIN).hasSkin());
        var old = new SettlementSnapshot("minecraft:overworld", 0, 0, 0, "{}", null, List.of(), List.of());
        assertFalse(old.hasSkin());
        assertEquals(SettlementSnapshot.DEFAULT_SKIN, old.mvpSkin());
    }

    /** 非默认 ID 一律视为「有皮肤」，具体是哪一款由客户端注册表解析。 */
    @Test void anyNonDefaultIdCountsAsHavingSkin() {
        assertTrue(snapshot("unknown_skin").hasSkin());
        assertTrue(snapshot("ender_dragon").hasSkin());
    }
}
