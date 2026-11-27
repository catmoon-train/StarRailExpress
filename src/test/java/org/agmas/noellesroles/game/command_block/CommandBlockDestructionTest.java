package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 破坏进度的半点刻度与异常修饰符的加权分配。 */
class CommandBlockDestructionTest {
    @Test
    void quotaKeepsMarkedCountPlusTwo() {
        assertEquals(3, CommandBlockRules.markedCount(2));
        assertEquals(5, CommandBlockRules.destructionQuota(2));
        // 半点刻度只是换单位，值与整次刻度一致
        assertEquals(10, CommandBlockRules.destructionQuotaHalves(2));
        assertEquals(CommandBlockRules.destructionQuota(3) * 2, CommandBlockRules.destructionQuotaHalves(3));
    }

    @Test
    void markedDeathIsHalfAKillerDeath() {
        assertEquals(2, CommandBlockRules.KILLER_DEATH_HALVES);
        assertEquals(1, CommandBlockRules.MARKED_DEATH_HALVES);
        // 两个带修饰符的死亡 = 一个杀手死亡
        assertEquals(CommandBlockRules.KILLER_DEATH_HALVES, CommandBlockRules.MARKED_DEATH_HALVES * 2);
    }

    @Test
    void abnormalWeightRewardsHighKillerChanceAndFactionCard() {
        double none = CommandBlockRules.abnormalWeight(0.0D, 1.0D);
        double high = CommandBlockRules.abnormalWeight(1.0D, 1.0D);
        double card = CommandBlockRules.abnormalWeight(0.0D, 6.0D);
        assertTrue(none > 0.0D, "没有杀手概率也仍可能被标成异常");
        assertTrue(high > none, "杀手概率越高越容易被标");
        assertTrue(card > none, "杀手阵营卡会放大被标的概率");
        // 倍率小于 1 视为没有加成，避免出现「有卡反而更难被标」
        assertEquals(none, CommandBlockRules.abnormalWeight(0.0D, 0.5D), 1.0E-9D);
        // 概率越界会被夹到 0~1
        assertEquals(high, CommandBlockRules.abnormalWeight(5.0D, 1.0D), 1.0E-9D);
        assertEquals(none, CommandBlockRules.abnormalWeight(-3.0D, 1.0D), 1.0E-9D);
    }

    @Test
    void stageUsesWholeDestructionUnits() {
        // 阶段换算仍按「整次」进度：半次不影响外观分档
        assertEquals(0, CommandBlockRules.destructionStage(0, 10));
        assertEquals(0, CommandBlockRules.destructionStage(3, 10));
        assertEquals(1, CommandBlockRules.destructionStage(4, 10));
        assertEquals(2, CommandBlockRules.destructionStage(7, 10));
        assertEquals(3, CommandBlockRules.destructionStage(10, 10));
    }
}
