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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 向周围玩家广播刺客形态命中斩击。 */
public final class StalkerPierceFx {
    private StalkerPierceFx() {
    }

    public static void broadcast(ServerPlayer attacker, Entity target, Vec3 direction) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 dir = direction.lengthSqr() < 1.0E-4D ? attacker.getViewVector(1.0F) : direction.normalize();
        StalkerPierceFxS2CPacket packet = new StalkerPierceFxS2CPacket(
                center.x, center.y, center.z, dir.x, dir.y, dir.z);
        for (ServerPlayer tracking : PlayerLookup.tracking(target)) {
            ServerPlayNetworking.send(tracking, packet);
        }
        ServerPlayNetworking.send(attacker, packet);
    }
}
