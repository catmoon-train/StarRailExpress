package io.wifi.starrailexpress.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.wifi.starrailexpress.client.util.SREClientUtils;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.level.Level;

public class SREPlayerUtils {
    public static List<UUID> getServerPlayersUUID(Level level) {
        List<UUID> result = new ArrayList<UUID>();
        if (level.isClientSide) {
            result.addAll(SREClientUtils.getAllPlayersUUID(level));
        } else {
            result.addAll(level.players().stream().map((p) -> p.getUUID()).toList());
        }
        return result;
    }

    public static boolean isPlayerAlive(Level level, UUID uid) {
        if (level.isClientSide) {
            return (SREClientUtils.isPlayerAlive(uid));
        } else {
            return GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(level.getPlayerByUUID(uid));
        }
    }
}
