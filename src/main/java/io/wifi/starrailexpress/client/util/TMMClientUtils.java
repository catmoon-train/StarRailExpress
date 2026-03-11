package io.wifi.starrailexpress.client.util;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class TMMClientUtils {

    public static UUID getPlayerUidByName(String name) {
        return Minecraft.getInstance().getConnection().getPlayerInfo(name).getProfile().getId();
    }
    
}
