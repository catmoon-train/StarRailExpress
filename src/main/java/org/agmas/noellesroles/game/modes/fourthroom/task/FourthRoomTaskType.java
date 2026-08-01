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

package org.agmas.noellesroles.game.modes.fourthroom.task;

public enum FourthRoomTaskType {
    DRINK_WATER("drink_water", "task.fourth_room.drink_water.description", 1, 3),
    USE_TOILET("use_toilet", "task.fourth_room.use_toilet.description", 1, 4),
    FIND_NOTE("find_note", "task.fourth_room.find_note.description", 2, 5),
    PHOTOGRAPH_BLOCK("photograph_block", "task.fourth_room.photograph_block.description", 2, 5);

    private final String id;
    private final String descriptionKey;
    private final int minReward;
    private final int maxReward;

    FourthRoomTaskType(String id, String descriptionKey, int minReward, int maxReward) {
        this.id = id;
        this.descriptionKey = descriptionKey;
        this.minReward = minReward;
        this.maxReward = maxReward;
    }

    public String id() {
        return id;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public int minReward() {
        return minReward;
    }

    public int maxReward() {
        return maxReward;
    }

    public static FourthRoomTaskType byId(String id) {
        for (FourthRoomTaskType value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }
}