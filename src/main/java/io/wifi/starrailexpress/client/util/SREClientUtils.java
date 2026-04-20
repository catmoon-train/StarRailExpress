package io.wifi.starrailexpress.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

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
}
