/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 *
 * <p>
 * 额外一层：持有该阵营「阵营卡」的玩家在本局带
 * {@link PlayerRoleWeightManager#getCardFactionBoost} 加成（见
 * {@code io.wifi.starrailexpress.backpack.FactionCardForcing}），
 * 抽取权重直接乘以倍率——这是「分了卡但模式没走硬强制」时的保底手段。
 */
public class PlayerRoleAssigner {
    /** 阵营卡加成下基础概率的下限：连续同阵营惩罚可能把概率压到接近 0，别让它把加成抹平。 */
    private static final double CARD_BOOST_MIN_WEIGHT = 5.0D;

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
            ServerPlayer candidate = candidates.get(i);
            double w = PlayerRoleWeightManager.getRoleWeightPercent(candidate, roleType) * 100;
            float boost = PlayerRoleWeightManager.getCardFactionBoost(candidate.getUUID(), roleType);
            if (boost > 1F) {
                // 阵营卡：连续同阵营惩罚可能把基础概率压到接近 0，这里给一个下限再乘倍率
                w = Math.max(w, CARD_BOOST_MIN_WEIGHT) * boost;
            } else if (w <= 0.5) {
                w = 0;
            }
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