package io.wifi.starrailexpress.settlement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 舞台编排的纯逻辑回归：站位、排数、尸堆入口与黑化判定。 */
class SettlementChoreographyTest {

    @Test void mvpStandsCenteredInFrontRow() {
        for (int count = 1; count <= 12; count++) {
            var mvp = SettlementChoreography.winner(0, count);
            assertEquals(0, mvp.x(), 1e-9, "MVP 必须在正中央");
            assertEquals(-.35, mvp.z(), 1e-9, "MVP 必须在前排");
        }
    }

    @Test void winnersUseAtMostTwoRowsAndStayCompact() {
        for (int count = 1; count <= 16; count++) {
            double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY, minX = Double.POSITIVE_INFINITY,
                    maxX = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                var slot = SettlementChoreography.winner(i, count);
                minZ = Math.min(minZ, slot.z()); maxZ = Math.max(maxZ, slot.z());
                minX = Math.min(minX, slot.x()); maxX = Math.max(maxX, slot.x());
            }
            java.util.Set<Double> rowHeights = new java.util.HashSet<>();
            for (int i = 0; i < count; i++) rowHeights.add(SettlementChoreography.winner(i, count).z());
            long rows = rowHeights.size();
            assertTrue(rows <= 2, "胜方最多两排，实际 " + rows + " 排（" + count + " 人）");
            assertTrue(maxZ - minZ <= 1.9, "两排间距不能拉得太开");
            assertTrue(maxX - minX <= 10.01, "站位必须收在舞台中央区域");
        }
    }

    @Test void winnersDoNotOverlapEachOther() {
        for (int count = 2; count <= 12; count++) {
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    var a = SettlementChoreography.winner(i, count);
                    var b = SettlementChoreography.winner(j, count);
                    double distance = Math.hypot(a.x() - b.x(), a.z() - b.z());
                    assertTrue(distance > .8, "两名胜方不能挤在同一点：" + distance);
                }
            }
        }
    }

    @Test void pileStaysCompactAndDropsAreOrdered() {
        for (int count = 1; count <= 20; count++) {
            assertTrue(SettlementChoreography.pileHalfWidth(count) <= 1.16);
            assertTrue(SettlementChoreography.pileHalfDepth(count) <= 1.01);
            assertTrue(SettlementChoreography.dropTick(count - 1, count) < SettlementSnapshot.INTRO_TICKS,
                    "所有尸体必须在开场内入场");
        }
        assertTrue(SettlementChoreography.dropTick(0, 20) <= SettlementChoreography.dropTick(1, 20));
        // 投放点集中在中央窄口，保证尸体互相压住而不是平铺。
        for (int i = 0; i < 20; i++) {
            var slot = SettlementChoreography.drop(i, 20);
            assertTrue(Math.abs(slot.x()) <= .6);
            assertTrue(Math.abs(slot.z() - SettlementChoreography.PILE_Z) <= .5);
        }
    }

    @Test void pileSitsBehindTheWinners() {
        assertTrue(SettlementChoreography.PILE_Z > SettlementChoreography.winner(0, 8).z() + 1,
                "尸堆必须在胜方后排之后");
        assertTrue(SettlementChoreography.REAR_FLOOR > 0, "尸堆展示台应略高于舞台地面");
    }

    @Test void silhouetteOnlyFollowsRealDeath() {
        assertTrue(SettlementChoreography.silhouette(true, false));
        assertTrue(SettlementChoreography.silhouette(false, true));
        assertFalse(SettlementChoreography.silhouette(false, false));
    }
}
