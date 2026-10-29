package io.wifi.starrailexpress.settlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 结算舞台地形注册表的通用行为。
 * <p>
 * 本体不内置任何具体地形，所以这里只用假 ID 验证分发与回退语义。
 *
 * <p><b>不要在这里碰 {@code Blocks.*}：</b>单元测试环境没有 bootstrap
 * Minecraft 注册表，访问 {@code Blocks} 会抛 {@code ExceptionInInitializerError}。
 * 因此地形实现一律返回 {@code null}，只验证「注册 / 查表 / 覆盖 / 校验」本身。
 */
class SettlementSkinsTest {

    @AfterEach
    void cleanup() {
        SettlementTerrains.clear();
    }

    @Test
    void unknownIdResolvesToNull() {
        assertNull(SettlementTerrains.byId(null));
        assertNull(SettlementTerrains.byId(""));
        assertNull(SettlementTerrains.byId("never_registered"));
        assertEquals(0, SettlementTerrains.size());
    }

    @Test
    void registeredTerrainIsFoundById() {
        SettlementTerrains.register("fake_skin", (x, y, z) -> null);
        assertEquals(1, SettlementTerrains.size());

        var terrain = SettlementTerrains.byId("fake_skin");
        assertNotNull(terrain);
        // 返回 null 表示「不接管这一格」，交回本体默认布置
        assertNull(terrain.blockAt(0, 0, 0));
    }

    @Test
    void registeringSameIdOverwrites() {
        SettlementTerrains.register("dup", (x, y, z) -> null);
        SettlementTerrains.Terrain second = (x, y, z) -> null;
        SettlementTerrains.register("dup", second);
        assertEquals(1, SettlementTerrains.size(), "同一 ID 只保留一份");
        assertSame(second, SettlementTerrains.byId("dup"), "后注册的实现必须覆盖先前的");
    }

    @Test
    void rejectsBlankIdAndNullTerrain() {
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register(null, (x, y, z) -> null));
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register("  ", (x, y, z) -> null));
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register("ok", null));
    }
}
