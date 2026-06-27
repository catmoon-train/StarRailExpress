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
        return this;
    }

    public void copyFrom(BackpackState other) {
        this.cards = new EnumMap<>(FactionCardType.class);
        if (other.cards != null) {
            this.cards.putAll(other.cards);
        }
        this.migrated = other.migrated;
        this.version = other.version;
        normalized();
    }
}
