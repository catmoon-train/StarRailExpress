package org.agmas.noellesroles.game.modes.fourthroom.card;

import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface Card {
    String id();

    CardCategory category();

    default boolean isSkill() {
        return category() == CardCategory.SKILL;
    }

    default boolean isInstantOnDraw() {
        return false;
    }

    default boolean canBeStolenOrDismantled() {
        return !isSkill();
    }

    default void onDraw(FourthRoomGameManager manager, UUID playerId, CardInstance instance) {
    }

    boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance);
}