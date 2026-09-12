/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.modifier.twin_children;

/** Minecraft-free helpers for Twin Children assignment. */
final class TwinChildrenAssignLogic {
    private TwinChildrenAssignLogic() {
    }

    enum Faction { INNOCENT, KILLER, INDEPENDENT_NEUTRAL }

    static boolean canReceive(Faction faction) {
        return faction == Faction.INNOCENT;
    }
}
