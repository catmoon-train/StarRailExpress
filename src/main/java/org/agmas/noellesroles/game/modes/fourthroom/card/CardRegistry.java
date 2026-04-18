package org.agmas.noellesroles.game.modes.fourthroom.card;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CardRegistry {
    private static final Map<String, Card> CARDS = new LinkedHashMap<>();

    static {
        Arrays.stream(BasicCard.values()).forEach(card -> CARDS.put(card.id(), card));
        Arrays.stream(SkillCard.values()).forEach(card -> CARDS.put(card.id(), card));
    }

    private CardRegistry() {
    }

    public static Card byId(String id) {
        return CARDS.get(id);
    }

    public static Collection<String> ids() {
        return CARDS.keySet();
    }
}