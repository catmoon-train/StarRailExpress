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

package net.exmo.sre.repair.role;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.state.*;
import net.exmo.sre.repair.arena.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RepairForcedRoleState {
    private static final Map<UUID, String> FORCED_ROLES = new ConcurrentHashMap<>();

    private RepairForcedRoleState() {
    }

    public static void force(UUID playerId, String roleId) {
        FORCED_ROLES.put(playerId, roleId);
    }

    public static Optional<RepairRoleDefinition> forcedRole(UUID playerId) {
        return Optional.ofNullable(FORCED_ROLES.get(playerId)).flatMap(RepairRoleDefinition::byId);
    }

    public static void clear(UUID playerId) {
        FORCED_ROLES.remove(playerId);
    }

    public static void clearAll() {
        FORCED_ROLES.clear();
    }
}
