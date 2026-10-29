package org.agmas.noellesroles.game.halloween;

import java.util.UUID;

/** 独立 javac/java 可运行，无需启动 Minecraft 或全项目 Gradle。 */
public final class HalloweenCombatStateTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID corpse = UUID.randomUUID();
        HalloweenCombatState state = new HalloweenCombatState();

        for (int i = 0; i < 1200; i++) state.tick(true);
        check(state.now() == 0, "暂停不得推进锯子、心脏、魂契、亡灵或冷却时钟");
        for (int i = 0; i < 10; i++) state.tick(false);
        check(state.now() == 10, "10 个有效 tick 后血锯才可结算");
        for (int i = 0; i < 300; i++) state.tick(true);
        check(state.now() == 10, "中途暂停不得改变截止时间");

        check(state.claimBody(corpse), "第一次可原子占用尸体");
        check(!state.claimBody(corpse), "骨剑、惊魂枪和 NPC 不能重复转换同一尸体");
        check(!state.claimBody(null), "不存在的尸体不能占用");
        check(state.claimBody(UUID.randomUUID()), "另一具尸体不受影响");

        check(!state.completeTask(alice, 3), "第一个任务没有魂契");
        check(!state.completeTask(alice, 3), "第二个任务没有魂契");
        check(!state.completeTask(bob, 3), "玩家任务进度相互隔离");
        check(state.completeTask(alice, 3), "第三个真实任务奖励魂契");
        check(!state.completeTask(alice, 3), "奖励后重新累计，不是每个任务都发");
        check(!state.completeTask(alice, 3), "第五个任务没有第二张魂契");
        check(state.completeTask(alice, 3), "第六个任务奖励第二张魂契");
        check(state.completeTask(bob, 2), "配置任务阈值生效");
        check(state.completeTask(bob, 0), "非法阈值防御性钳制为一");

        check(state.acceptPact(alice), "每局首次魂契可接受");
        check(state.hasAcceptedPact(alice), "接受记录存在");
        check(!state.acceptPact(alice), "不可重复登记，死亡后也不能再买复活");
        check(state.acceptPact(bob), "另一玩家可以登记");
        state.disconnect(alice);
        check(!state.acceptPact(alice), "掉线重连不恢复一次性资格");
        state.completeTask(alice, 3);
        state.disconnect(alice);
        check(!state.completeTask(alice, 3), "掉线清理未完成任务计数");

        HalloweenCombatState nextRound = new HalloweenCombatState();
        check(nextRound.now() == 0, "新局时钟重置");
        check(nextRound.acceptPact(alice), "新局可以重新获得一次性资格");
        check(nextRound.claimBody(corpse), "尸体事务不跨局泄漏");
        check(state.now() == 10, "多个世界状态互不推进");
        System.out.println("HalloweenCombatStateTest: 26 checks passed");
    }
}
