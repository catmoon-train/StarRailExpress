/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.gunfx;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 将潜行者实际通过的冲刺分段同步给周围玩家。 */
public final class StalkerDashTrails {
    private StalkerDashTrails() {
    }

    public static void broadcast(ServerPlayer player, Vec3 from, Vec3 to, boolean attackDash) {
        StalkerDashTrailS2CPacket packet = new StalkerDashTrailS2CPacket(player.getId(),
                from.x, from.y, from.z, to.x, to.y, to.z, attackDash);
        for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
            ServerPlayNetworking.send(tracking, packet);
        }
        ServerPlayNetworking.send(player, packet);
    }
}
