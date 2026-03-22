package io.wifi.starrailexpress.client.util;

import net.minecraft.client.Minecraft;

import java.util.UUID;

public class TMMClientUtils {

    public static UUID getPlayerUidByName(String name) {
        return Minecraft.getInstance().getConnection().getPlayerInfo(name).getProfile().getId();
    }
    
}
