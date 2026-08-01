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

package org.agmas.noellesroles.game.modes.fourthroom.card;

import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public enum SkillCard implements Card {
    REBUILD("rebuild") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.shuffleDiscardIntoDeck(playerId);
            manager.drawCards(playerId, 2, false);
            return true;
        }
    },
    INTERROGATE("interrogate") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return manager.interrogateOpponent(playerId, targetId);
        }
    },
    DECOY("decoy") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.armDecoy(playerId);
            return true;
        }
    },
    FIRST_AID("first_aid") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            return manager.restoreOneIdentity(playerId);
        }
    },
    FATE_SHIFT("fate_shift") {
        @Override
        public boolean play(FourthRoomGameManager manager, UUID playerId, @Nullable UUID targetId, CardInstance instance) {
            manager.reduceMarkedKill(playerId, 1);
            manager.peekTopCards(playerId, 2);
            return true;
        }
    };

    private final String id;

    SkillCard(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CardCategory category() {
        return CardCategory.SKILL;
    }

    @Override
    public boolean canBeStolenOrDismantled() {
        return false;
    }
}