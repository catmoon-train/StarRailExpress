package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 职业多样性选池的纯规则，不依赖世界或职业注册表。 */
final class CommandBlockRoleSelection {
    private CommandBlockRoleSelection() {
    }

    /**
     * 先选本局累计出现最少的职业，再选当前存活人数最少的职业，同分候选由调用方随机。
     * 当前人数也是累计次数的下限，避免外部改职产生的现有职业被当作从未出现。
     * 只从传入的启用池中选择，池为空则返回空列表。
     */
    static <T> List<T> candidates(List<T> pool, Map<T, Integer> uses, Map<T, Integer> living) {
        List<T> result = new ArrayList<>();
        int bestUses = Integer.MAX_VALUE;
        int bestLiving = Integer.MAX_VALUE;
        for (T role : pool) {
            int livingCount = living.getOrDefault(role, 0);
            int usedCount = Math.max(uses.getOrDefault(role, 0), livingCount);
            if (usedCount < bestUses || usedCount == bestUses && livingCount < bestLiving) {
                result.clear();
                bestUses = usedCount;
                bestLiving = livingCount;
            }
            if (usedCount == bestUses && livingCount == bestLiving) {
                result.add(role);
            }
        }
        return result;
    }
}
