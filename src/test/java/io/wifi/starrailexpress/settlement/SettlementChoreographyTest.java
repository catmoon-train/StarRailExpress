package io.wifi.starrailexpress.settlement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

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
            assertTrue(maxX - minX <= 13.01, "站位必须收在舞台中央区域");
        }
    }

    @Test void winnersDoNotOverlapEachOther() {
        for (int count = 2; count <= 12; count++) {
            for (int i = 0; i < count; i++) {
                for (int j = i + 1; j < count; j++) {
                    var a = SettlementChoreography.winner(i, count);
                    var b = SettlementChoreography.winner(j, count);
                    double distance = Math.hypot(a.x() - b.x(), a.z() - b.z());
                    assertTrue(distance > 1.0, "胜方要拉开间距，不能挤在一起：" + distance);
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

    @Test void pileSitsBehindTheWinnersOnBareFloor() {
        assertTrue(SettlementChoreography.PILE_Z > SettlementChoreography.winner(0, 8).z() + 1,
                "尸堆必须在胜方后排之后");
        assertEquals(0, SettlementChoreography.REAR_FLOOR, 1e-9,
                "尸体直接落在舞台地板上，不能再被方块围台抬起来");
    }

    @Test void corpsesFallAfterWinnersLand() {
        // 胜方约 130 tick 全部落地并开始摆动作，尸体必须更晚才出现。
        assertTrue(SettlementChoreography.DROP_START > 130, "尸体不能与胜方同时下落");
        for (int count = 1; count <= 20; count++) {
            assertTrue(SettlementChoreography.dropTick(count - 1, count) >= SettlementChoreography.DROP_START);
            assertTrue(SettlementChoreography.dropTick(count - 1, count) < SettlementSnapshot.INTRO_TICKS,
                    "全部尸体仍要在开场内落完");
        }
    }

    @Test void replayKeepsOnlyMvpRelatedEvents() {
        UUID mvp = UUID.randomUUID(), other = UUID.randomUUID();
        var start = new SettlementSnapshot.Event(0, "GAME_START", null, null, "{}", null, null, null);
        var mine = new SettlementSnapshot.Event(10, "PLAYER_KILL", mvp, other, "{}", null, null, "sre:knife");
        var theirs = new SettlementSnapshot.Event(20, "PLAYER_KILL", other, UUID.randomUUID(), "{}", null, null, null);
        var victimOfMvp = new SettlementSnapshot.Event(30, "TASK_COMPLETE", other, null, "{}", null, null, null);
        var end = new SettlementSnapshot.Event(40, "GAME_END", null, null, "{}", null, null, null);
        var kept = SettlementSnapshot.mvpTimeline(mvp, java.util.List.of(start, mine, theirs, victimOfMvp, end));
        assertEquals(3, kept.size(), "只保留 MVP 相关事件与开/终局锚点");
        assertTrue(kept.contains(mine));
        assertTrue(kept.contains(start));
        assertTrue(kept.contains(end));
        assertFalse(kept.contains(theirs));
        assertTrue(SettlementSnapshot.involves(mvp, mine));
        assertFalse(SettlementSnapshot.involves(mvp, theirs));
    }

    @Test void livingWinnersStandInTheMiddleAndDeadOnTheSides() {
        UUID mvp = UUID.randomUUID();
        var alive = new SettlementSnapshot.Actor(UUID.randomUUID(), "a", "", 0, true, false, false, 3, 0);
        var dead = new SettlementSnapshot.Actor(UUID.randomUUID(), "d", "", 0, true, true, false, 1, 0);
        var center = new SettlementSnapshot.Actor(mvp, "m", "", 0, true, false, false, 5, 0);
        var ordered = SettlementChoreography.winnerOrder(java.util.List.of(dead, alive, center), mvp);
        assertEquals(center, ordered.get(0), "MVP 必须排第一（正中）");
        assertEquals(alive, ordered.get(1), "存活者应排在死亡者内侧");
        assertEquals(dead, ordered.get(2), "死亡者应被推到两侧");
        // 4 人时序号 2、3 分列两侧最外层，死亡者排在它们的位置上。
        double inner = Math.abs(SettlementChoreography.winner(1, 4).x());
        double outer = Math.abs(SettlementChoreography.winner(2, 4).x());
        assertTrue(outer > inner, "外侧序号必须比内侧更远离中心：" + outer + " vs " + inner);
    }

    @Test void onlyDeadWinnersTurnBlack() {
        assertTrue(SettlementChoreography.silhouette(true, true), "获胜阵营的死者要变黑");
        assertFalse(SettlementChoreography.silhouette(true, false), "获胜阵营的存活者不能变黑");
        assertFalse(SettlementChoreography.silhouette(false, true), "落败者即使死亡也保持原色");
        assertFalse(SettlementChoreography.silhouette(false, false));
    }
}
