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

package io.wifi.starrailexpress.api.replay;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ReplayPlayerProfile(
        @Nullable UUID uuid,
        String name,
        @Nullable String roleId,
        Component roleName,
        boolean alive) {
    public static ReplayPlayerProfile unknown() {
        return new ReplayPlayerProfile(null, "Unknown", null,
                Component.translatable("sre.replay.event.unknown_player"), false);
    }
}
