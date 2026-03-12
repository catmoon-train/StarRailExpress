package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface MatrixParticleManager {
    static Vec3 muzzlePosForPlayer$get(Player playerEntity) {
        Vec3 pos = SREClient.particleMap.getOrDefault(playerEntity, null);
        SREClient.particleMap.remove(playerEntity);
        return pos;
    }

    static void muzzlePosForPlayer$set(Player playerEntity, Vec3 vec3d) {
        SREClient.particleMap.put(playerEntity, vec3d);
    }
}
