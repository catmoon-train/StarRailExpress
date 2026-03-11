package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface MatrixParticleManager {
    static Vec3 getMuzzlePosForPlayer(Player playerEntity) {
        Vec3 pos = SREClient.particleMap.getOrDefault(playerEntity, null);
        SREClient.particleMap.remove(playerEntity);
        return pos;
    }

    static void setMuzzlePosForPlayer(Player playerEntity, Vec3 vec3d) {
        SREClient.particleMap.put(playerEntity, vec3d);
    }
}
