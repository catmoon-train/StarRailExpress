package io.wifi.starrailexpress.settlement;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 结算皮肤注册表的通用行为。
 * <p>
 * 本体不内置任何具体皮肤，所以这里只用假 ID 验证分发与回退语义。
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
        BlockState endStone = Blocks.END_STONE.defaultBlockState();
        SettlementTerrains.register("fake_skin", (x, y, z) -> y == 0 ? endStone : null);
        assertEquals(1, SettlementTerrains.size());

        var terrain = SettlementTerrains.byId("fake_skin");
        assertNotNull(terrain);
        assertSame(endStone, terrain.blockAt(0, 0, 0));
        assertNull(terrain.blockAt(0, 1, 0), "未接管的格子必须返回 null 交回默认布置");
    }

    @Test
    void registeringSameIdOverwrites() {
        BlockState a = Blocks.END_STONE.defaultBlockState();
        BlockState b = Blocks.OBSIDIAN.defaultBlockState();
        SettlementTerrains.register("dup", (x, y, z) -> a);
        SettlementTerrains.register("dup", (x, y, z) -> b);
        assertEquals(1, SettlementTerrains.size(), "同一 ID 只保留一份");
        assertSame(b, SettlementTerrains.byId("dup").blockAt(0, 0, 0));
    }

    @Test
    void rejectsBlankIdAndNullTerrain() {
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register(null, (x, y, z) -> null));
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register("  ", (x, y, z) -> null));
        assertThrows(IllegalArgumentException.class, () -> SettlementTerrains.register("ok", null));
    }
}
