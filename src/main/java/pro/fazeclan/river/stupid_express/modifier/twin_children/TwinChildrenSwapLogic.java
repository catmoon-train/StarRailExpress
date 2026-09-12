/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

/** Minecraft-free helpers for Twin Children position swapping. */
final class TwinChildrenSwapLogic {
    static final int INTERVAL_SECONDS = 60;

    private TwinChildrenSwapLogic() {
    }

    static int intervalTicks() {
        return INTERVAL_SECONDS * 20;
    }

    static boolean due(long gameTicks, long nextSwapAt) {
        return gameTicks >= nextSwapAt;
    }

    static long scheduleNext(long gameTicks, int intervalTicks) {
        return gameTicks + intervalTicks;
    }
}
