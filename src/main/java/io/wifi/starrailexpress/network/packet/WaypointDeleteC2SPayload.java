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

package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.data.WaypointManager;
import io.wifi.starrailexpress.util.WaypointSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * C2S —— 客户端（管理 GUI / 看向删除）请求删除路径点。
 *
 * <p>{@code wholePath=true} 时删除整条 {@code path}（忽略 name），否则删除单个 (path,name)。
 * 删除限 OP（{@code hasPermissions(2)}），落库后由 {@link WaypointSync#syncToAll} 重广播全量。</p>
 */
public record WaypointDeleteC2SPayload(String path, String name, boolean wholePath) implements CustomPacketPayload {

    public static final Type<WaypointDeleteC2SPayload> ID = new Type<>(SRE.id("waypoint_delete"));

    public static final StreamCodec<FriendlyByteBuf, WaypointDeleteC2SPayload> CODEC =
            StreamCodec.ofMember(WaypointDeleteC2SPayload::write, WaypointDeleteC2SPayload::read);

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(path);
        buf.writeUtf(name);
        buf.writeBoolean(wholePath);
    }

    public static WaypointDeleteC2SPayload read(FriendlyByteBuf buf) {
        return new WaypointDeleteC2SPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    @Override
    public Type<WaypointDeleteC2SPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<WaypointDeleteC2SPayload> {
        @Override
        public void receive(@NotNull WaypointDeleteC2SPayload payload,
                            ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                // 删除限 OP
                if (!player.hasPermissions(2)) {
                    return;
                }
                WaypointManager manager = WaypointManager.get(server);
                if (payload.wholePath()) {
                    manager.removePath(payload.path());
                } else {
                    manager.removeWaypoint(payload.path(), payload.name());
                }
                WaypointSync.syncToAll(server);
            });
        }
    }
}
