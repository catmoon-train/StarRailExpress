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