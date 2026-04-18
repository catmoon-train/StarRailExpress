package org.agmas.noellesroles.game.modes.fourthroom.game;

import org.agmas.noellesroles.game.modes.fourthroom.card.BasicCard;
import org.agmas.noellesroles.game.modes.fourthroom.card.CardInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * 牌组预设 - 定义固定的抽牌堆配置
 */
public enum DeckPreset {
    /**
     * 标准牌组 - 默认平衡配置
     */
    STANDARD("standard", "标准牌组") {
        @Override
        public List<CardInstance> createDeck() {
            List<CardInstance> deck = new ArrayList<>();
            // 死亡卡 x3
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            // 净化 x1
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            // 抽底（金卡）x1
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            // 夺取 x2
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            // 跳过 x1
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            // 否决 x1
            deck.add(new CardInstance(BasicCard.VETO.id(), false));
            // 点杀 x1 + 点杀（金卡）x1
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), false));
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), true));
            // 拆解 x1
            deck.add(new CardInstance(BasicCard.DISMANTLE.id(), false));
            // 窥视 x1
            deck.add(new CardInstance(BasicCard.PEEK.id(), false));
            // 复制 x1
            deck.add(new CardInstance(BasicCard.COPY.id(), false));
            // 命格 x2
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            return deck;
        }
    },

    /**
     * 激进牌组 - 更多攻击性卡牌
     */
    AGGRESSIVE("aggressive", "激进牌组") {
        @Override
        public List<CardInstance> createDeck() {
            List<CardInstance> deck = new ArrayList<>();
            // 死亡卡 x4 (增加风险)
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            // 净化 x1
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            // 抽底（金卡）x1
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            // 夺取 x3 (更多抢夺)
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            // 跳过 x2
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            // 否决 x1
            deck.add(new CardInstance(BasicCard.VETO.id(), false));
            deck.add(new CardInstance(BasicCard.VETO.id(), false));
            // 点杀 x2 + 点杀（金卡）x1
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), false));
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), false));
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), true));
            // 拆解 x2
            deck.add(new CardInstance(BasicCard.DISMANTLE.id(), false));
            deck.add(new CardInstance(BasicCard.DISMANTLE.id(), false));
            // 窥视 x1
            deck.add(new CardInstance(BasicCard.PEEK.id(), false));
            deck.add(new CardInstance(BasicCard.PEEK.id(), true));
            deck.add(new CardInstance(BasicCard.PEEK.id(), true));
            deck.add(new CardInstance(BasicCard.PEEK.id(), false));
            // 命格 x1 (减少防御)
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            deck.add(new CardInstance(BasicCard.LIFE.id(), true));
            return deck;
        }
    },

    /**
     * 防御牌组 - 更多生存和控场卡牌
     */
    DEFENSIVE("defensive", "防御牌组") {
        @Override
        public List<CardInstance> createDeck() {
            List<CardInstance> deck = new ArrayList<>();
            // 死亡卡 x2 (减少风险)
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            // 净化 x2 (更多洗牌)
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
            // 抽底（金卡）x2
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
            // 夺取 x1
            deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
            // 跳过 x1
            deck.add(new CardInstance(BasicCard.SKIP.id(), false));
            // 否决 x2 (更多干扰)
            deck.add(new CardInstance(BasicCard.VETO.id(), false));
            deck.add(new CardInstance(BasicCard.VETO.id(), false));
            // 点杀 x1 + 点杀（金卡）x1
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), false));
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), true));
            // 拆解 x1
            deck.add(new CardInstance(BasicCard.DISMANTLE.id(), false));
            // 窥视 x2 (更多信息)
            deck.add(new CardInstance(BasicCard.PEEK.id(), false));
            deck.add(new CardInstance(BasicCard.PEEK.id(), false));
            // 命格 x3 (更多防御)
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            deck.add(new CardInstance(BasicCard.LIFE.id(), false));
            return deck;
        }
    },

    /**
     * 混乱牌组 - 完全随机化，每次游戏不同
     */
    CHAOS("chaos", "混乱牌组") {
        @Override
        public List<CardInstance> createDeck() {
            // 混沌模式使用标准牌组但会在初始化时额外打乱
            List<CardInstance> deck = STANDARD.createDeck();
            // 添加额外的特殊卡牌
            deck.add(new CardInstance(BasicCard.DEATH.id(), false));
            deck.add(new CardInstance(BasicCard.POINT_KILL.id(), true));
            return deck;
        }
    };

    private final String id;
    private final String displayName;

    DeckPreset(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 创建该预设的牌组实例
     * @return 卡牌实例列表
     */
    public abstract List<CardInstance> createDeck();

    /**
     * 根据ID查找牌组预设
     */
    public static DeckPreset byId(String id) {
        if (id == null || id.isBlank()) {
            return STANDARD;
        }
        for (DeckPreset preset : values()) {
            if (preset.id().equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return STANDARD;
    }
}
