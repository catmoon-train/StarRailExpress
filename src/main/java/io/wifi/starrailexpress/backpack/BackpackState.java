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

package io.wifi.starrailexpress.backpack;

import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 场外背包数据模型（Gson POJO）。卡牌以计数形式存储，复用通行证的 {@link FactionCardType}。
 * 序列化格式与 {@code progression} 分区里的 {@code factionCards} 字节兼容，迁移值可直接搬运。
 */
public final class BackpackState {
    public Map<FactionCardType, Integer> cards = new EnumMap<>(FactionCardType.class);
    /** 一次性「移动」迁移守卫：通行证卡牌已搬入背包后置 true。 */
    public boolean migrated = false;
    /** 各阵营卡最近一次成功激活的墙钟时间（epoch ms）。用于同种类使用间隔。 */
    public Map<FactionCardType, Long> cardLastUsedAt = new EnumMap<>(FactionCardType.class);
    public long version;

    public static BackpackState createDefault() {
        BackpackState state = new BackpackState();
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                state.cards.put(type, 0);
            }
        }
        return state;
    }

    public BackpackState normalized() {
        if (cards == null) {
            cards = new EnumMap<>(FactionCardType.class);
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                cards.putIfAbsent(type, 0);
            }
        }
        // 钳制负值
        cards.replaceAll((type, count) -> count == null ? 0 : Math.max(0, count));
        cardLastUsedAt = normalizeLastUsedAt(cardLastUsedAt);
        return this;
    }

    public long lastUsedAt(FactionCardType type) {
        if (cardLastUsedAt == null || type == null || type == FactionCardType.NONE) {
            return 0L;
        }
        Long value = cardLastUsedAt.get(type);
        return value == null ? 0L : Math.max(0L, value);
    }

    public void markUsed(FactionCardType type, long epochMs) {
        if (type == null || type == FactionCardType.NONE || epochMs <= 0L) {
            return;
        }
        if (cardLastUsedAt == null) {
            cardLastUsedAt = new EnumMap<>(FactionCardType.class);
        }
        cardLastUsedAt.put(type, epochMs);
    }

    public void copyFrom(BackpackState other) {
        this.cards = new EnumMap<>(FactionCardType.class);
        if (other.cards != null) {
            this.cards.putAll(other.cards);
        }
        this.migrated = other.migrated;
        this.version = other.version;
        this.cardLastUsedAt = other.cardLastUsedAt;
        normalized();
    }

    private static Map<FactionCardType, Long> normalizeLastUsedAt(Map<FactionCardType, Long> raw) {
        EnumMap<FactionCardType, Long> used = new EnumMap<>(FactionCardType.class);
        if (raw == null) {
            return used;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            FactionCardType type = parseType(entry.getKey());
            if (type == FactionCardType.NONE) {
                continue;
            }
            long epoch = toEpoch(entry.getValue());
            if (epoch > 0L) {
                used.put(type, epoch);
            }
        }
        return used;
    }

    private static FactionCardType parseType(Object key) {
        if (key instanceof FactionCardType type) {
            return type;
        }
        if (key == null) {
            return FactionCardType.NONE;
        }
        return FactionCardType.fromString(String.valueOf(key));
    }

    private static long toEpoch(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
