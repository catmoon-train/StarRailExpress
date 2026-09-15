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

package org.agmas.noellesroles.content.speaker;

import io.wifi.starrailexpress.event.OnGameEnd;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.SpeakerItem;
import org.agmas.noellesroles.packet.SpeakerS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端音响开关：写入物品 NBT，并向附近玩家同步播放状态。
 */
public final class SpeakerPlayback {
    private static final Map<UUID, String> ACTIVE = new HashMap<>();

    private SpeakerPlayback() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SpeakerPlayback::tick);
        OnGameEnd.EVENT.register((level, game) -> clearAll(level.getServer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player, true));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncAllTo(handler.player));
    }

    public static void apply(ServerPlayer player, String trackId, boolean playing) {
        ItemStack stack = SpeakerItem.findInInventory(player);
        if (stack.isEmpty()) {
            return;
        }
        if (playing && SpeakerTracks.byId(trackId) == null) {
            return;
        }
        SpeakerItem.writeState(stack, playing ? trackId : SpeakerItem.getTrackId(stack), playing);
        if (playing) {
            ACTIVE.put(player.getUUID(), trackId);
            broadcast(player, trackId, true);
        } else {
            ACTIVE.remove(player.getUUID());
            broadcast(player, trackId, false);
        }
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (UUID id : Map.copyOf(ACTIVE).keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !SpeakerItem.hasPlayingSpeaker(player)) {
                if (player != null) {
                    ItemStack stack = SpeakerItem.findInInventory(player);
                    if (!stack.isEmpty()) {
                        SpeakerItem.writeState(stack, SpeakerItem.getTrackId(stack), false);
                    }
                    broadcast(player, "", false);
                }
                ACTIVE.remove(id);
            }
        }
    }

    private static void stop(ServerPlayer player, boolean broadcast) {
        if (player == null) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        if (broadcast) {
            broadcast(player, "", false);
        }
    }

    private static void clearAll(MinecraftServer server) {
        if (server == null) {
            ACTIVE.clear();
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack stack = SpeakerItem.findInInventory(player);
            if (!stack.isEmpty()) {
                SpeakerItem.writeState(stack, SpeakerItem.getTrackId(stack), false);
            }
            broadcast(player, "", false);
        }
        ACTIVE.clear();
    }

    private static void syncAllTo(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return;
        }
        for (Map.Entry<UUID, String> entry : ACTIVE.entrySet()) {
            ServerPlayNetworking.send(viewer, new SpeakerS2CPacket(entry.getKey(), entry.getValue(), true));
        }
    }

    private static void broadcast(ServerPlayer player, String trackId, boolean playing) {
        SpeakerS2CPacket packet = new SpeakerS2CPacket(player.getUUID(), trackId, playing);
        for (ServerPlayer viewer : PlayerLookup.world(player.serverLevel())) {
            ServerPlayNetworking.send(viewer, packet);
        }
    }
}
