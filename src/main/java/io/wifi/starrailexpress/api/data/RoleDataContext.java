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

package io.wifi.starrailexpress.api.data;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record RoleDataContext(Player player, @Nullable SRERole role, @Nullable Runnable syncFunc,
        @Nullable Consumer<ServerPlayer> syncToFunc) {
    public void sync() {
        if (syncFunc != null)
            syncFunc.run();
    }

    public Player getPlayer() {
        return player();
    }

    public boolean isClientSide() {
        return player.level().isClientSide;
    }

    public void syncTo(ServerPlayer player2) {
        if (syncToFunc != null)
            syncToFunc.accept(player2);
    }
}
