package org.agmas.noellesroles.game.command_block;

import java.util.List;
import java.util.function.Function;

/** 裂缝选点的有界重试与距离规则，不依赖游戏注册表。 */
public final class CommandBlockCrackSpawnRules {
    public static final int MAX_ATTEMPTS = 5;
    public static final double PLAYER_RANGE = 60.0;

    private CommandBlockCrackSpawnRules() {}

    public static boolean withinPlayerRange(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= PLAYER_RANGE * PLAYER_RANGE;
    }

    /** 候选由调用方随机排序；最多检查五个任务点，失败不使用不合格位置兜底。 */
    public static <T, R> R choose(List<T> candidates, Function<T, R> attempt) {
        for (int i = 0; i < Math.min(MAX_ATTEMPTS, candidates.size()); i++) {
            R result = attempt.apply(candidates.get(i));
            if (result != null) return result;
        }
        return null;
    }
}
