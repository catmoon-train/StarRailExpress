package org.agmas.harpymodloader.modded_murder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.List;

/**
 * 根据玩家对各职业类型的历史权重，加权随机分配职业。
 *
 * 核心规则：
 * - 权重越高 → 被分配该类型职业的概率越低（反向权重）
 * - 每次成功分配后，该玩家对应类型的权重 +1
 *
 * 抽取公式：每位玩家的抽中概率 = (1.0 / weight) / Σ(1.0 / weight_i)
 */
public class PlayerRoleAssigner {

    public static Player pickByInverseWeightAndRemove(List<ServerPlayer> candidates, int roleType) {
        Collections.shuffle(candidates);
        Player selected = pickByInverseWeight(candidates, roleType);
        candidates.remove(selected);
        return selected;
    }

    /**
     * 反向权重加权随机抽取。
     *
     * 概率 ∝ 1.0 / weight
     * 权重为 1（初始值）时概率最高；随着权重增大概率逐渐降低。
     *
     * @param candidates 候选玩家
     * @param roleType   职业类型（对应 WeightInfo 中的 key）
     * @return 被选中的玩家
     */
    public static Player pickByInverseWeight(List<ServerPlayer> candidates, int roleType) {
        if (candidates.isEmpty())
            return null;
        // 1. 计算每位玩家的反向权重，并累加总和
        Collections.shuffle(candidates);
        
        double[] inverseWeights = new double[candidates.size()];
        double total = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            double w = PlayerRoleWeightManager.getRoleWeightPercent(candidates.get(i), roleType) * 100;
            if (w <= 0.5)
                w = 0;
            // 防止 weight <= 0 导致除零或负概率
            inverseWeights[i] = w;
            total += w;
        }

        // 2. 在 [0, total) 范围内随机一个值，按累积区间命中对应玩家
        double rand = Math.random() * total;
        double cumulative = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            cumulative += inverseWeights[i];
            if (rand < cumulative) {
                return candidates.get(i);
            }
        }

        // 浮点误差兜底：返回最后一个
        return candidates.get(candidates.size() - 1);
    }
}