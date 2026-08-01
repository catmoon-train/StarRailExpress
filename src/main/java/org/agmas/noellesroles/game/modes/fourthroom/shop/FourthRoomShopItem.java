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

package org.agmas.noellesroles.game.modes.fourthroom.shop;

public enum FourthRoomShopItem {
    SCORPION("scorpion"),
    HANDGUN("handgun"),
    POISON_MUSHROOM("poison_mushroom"),
    BULLETPROOF_VEST("bulletproof_vest"),
    TEST_STRIP("test_strip"),
    STICKY_NOTE("sticky_note");

    private final String id;

    FourthRoomShopItem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static FourthRoomShopItem byId(String id) {
        for (FourthRoomShopItem value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }
}