package io.wifi.starrailexpress.content.vote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 职业池投票的取票逻辑（{@link RolePoolVote#rankedIndices}）：
 * 票高者优先、同票随机、得票不足时随机补齐。
 */
class RolePoolVoteRankingTest {

    @Test
    void highestTalliesComeFirst() {
        assertEquals(List.of(1, 2), RolePoolVote.rankedIndices(List.of(0, 5, 3), 2, new Random(1)));
        assertEquals(List.of(0), RolePoolVote.rankedIndices(List.of(9, 1), 1, new Random(1)));
    }

    @Test
    void tiesAreDecidedByTheGivenRandom() {
        List<Integer> counts = List.of(2, 2, 2, 2);
        assertEquals(RolePoolVote.rankedIndices(counts, 2, new Random(42)),
                RolePoolVote.rankedIndices(counts, 2, new Random(42)));
    }

    @Test
    void zeroVotePoolsFillTheRemainingSlotsRandomly() {
        List<Integer> counts = List.of(0, 0, 0, 0);
        // 生产里用的是同一个静态 Random，所以这里也用同一个随机源连续抽 200 次
        Random shared = new Random(2026);
        Set<Integer> firstPicks = new HashSet<>();
        Set<String> combos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            List<Integer> picked = RolePoolVote.rankedIndices(counts, 2, shared);
            firstPicks.add(picked.get(0));
            combos.add(picked.toString());
        }
        // 零票时也是随机补位：4 个池子都可能被先选中，组合也不止一种固定搭配
        assertEquals(Set.of(0, 1, 2, 3), firstPicks);
        assertTrue(combos.size() >= 6, "零票补位的组合应有多种，实际只有 " + combos.size() + " 种");
    }

    @Test
    void tiedTopTalliesOnlyPickFromTheTiedOnes() {
        List<Integer> counts = List.of(0, 3, 3, 3);
        for (int seed = 0; seed < 50; seed++) {
            List<Integer> picked = RolePoolVote.rankedIndices(counts, 2, new Random(seed));
            assertEquals(2, picked.size());
            assertTrue(picked.stream().allMatch(index -> index >= 1), "0 票的选项不该挤掉同票的选项：" + picked);
        }
        // 两个 5 票遥遥领先时结果固定
        assertEquals(List.of(1, 2), RolePoolVote.rankedIndices(List.of(1, 5, 5, 0), 2, new Random(7)));
    }
    @Test
    void selectIsClampedToTheOptionCount() {
        assertEquals(3, RolePoolVote.rankedIndices(List.of(1, 1, 1), 99, new Random(7)).size());
        assertTrue(RolePoolVote.rankedIndices(List.of(), 2, new Random(1)).isEmpty());
        assertTrue(RolePoolVote.rankedIndices(List.of(1, 1), 0, new Random(1)).isEmpty());
    }

    @Test
    void resultNeverRepeatsAnIndex() {
        List<Integer> picked = RolePoolVote.rankedIndices(List.of(3, 3, 3, 3), 3, new Random(5));
        assertEquals(3, new HashSet<>(picked).size());
    }
}
