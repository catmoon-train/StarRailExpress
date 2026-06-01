package io.wifi.starrailexpress.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SREClientUtils {
    public static UUID getPlayerUidByName(String name) {
        if (name == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(name);
        if (s == null)
            return null;
        return s.getProfile().getId();
    }

    public static String getPlayerNameByUid(UUID uid) {
        if (uid == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return null;
        return s.getProfile().getName();
    }

    public static PlayerInfo getPlayerInfoByUid(UUID uid) {
        if (uid == null)
            return null;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return null;
        return s;
    }

    public static List<UUID> getAllPlayersUUID(Level level) {
        if (level.isClientSide) {
            List<UUID> result = new ArrayList<UUID>();
            for (PlayerInfo op : Minecraft.getInstance().getConnection().getOnlinePlayers()) {
                result.add(op.getProfile().getId());
            }
            return result;
        }
        return level.players().stream().map((p) -> p.getUUID()).toList();
    }

    public static boolean isPlayerAlive(UUID uid) {
        if (uid == null)
            return false;
        var s = Minecraft.getInstance().getConnection().getPlayerInfo(uid);
        if (s == null)
            return false;
        // 下面相当于 gamemode == SURVIVAL || gamemode == ADVENTURE;
        return s.getGameMode().isSurvival();
    }
}
