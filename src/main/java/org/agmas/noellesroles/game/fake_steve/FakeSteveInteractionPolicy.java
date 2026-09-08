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

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;

/** Observable interaction rules kept separate from Minecraft packet plumbing. */
public final class FakeSteveInteractionPolicy {
    public static final long SNACK_COOLDOWN_TICKS = 50L * 20L;
    public static final int SATISFIED_FOOD_LEVEL = 16;

    private FakeSteveInteractionPolicy() {
    }

    public static boolean isHungry(int foodLevel) {
        return foodLevel < SATISFIED_FOOD_LEVEL;
    }

    public static boolean shouldSnack(boolean hungry, boolean onCooldown, boolean hasEatOrDrinkTask) {
        if (onCooldown) {
            return false;
        }
        return hasEatOrDrinkTask || hungry;
    }

    public static boolean shouldTakeFromPlate(boolean plateEmpty, boolean onCooldown,
            boolean alreadyUsedThisPlate, boolean maySnack) {
        return maySnack && !plateEmpty && !onCooldown && !alreadyUsedThisPlate;
    }

    public static double maxInteractionDistance(Task task) {
        return task == Task.CHAIR || task == Task.TOILET ? 1.4D : 2.75D;
    }

    public static boolean maintainsUseAnimation(Task task) {
        return task == Task.EAT || task == Task.DRINK;
    }

    public static boolean swingsHand(Task task) {
        return task != null;
    }

    public static boolean releasesPostureAfterCompletion(Task task) {
        return task == Task.CHAIR || task == Task.TOILET || task == Task.SLEEP
                || task == Task.RAED_BOOK;
    }
}
