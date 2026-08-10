package org.agmas.noellesroles.utils;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class StuckHelperUtils {

    public static final double volumeThreshold = 0.03;

    public static boolean isPlayerStuck(Player player) {
        if (player.getVehicle() != null)
            return false;
        if (player.isSleeping())
            return false;
        Level level = player.level();
        AABB playerBox = player.getBoundingBox();

        return level.collidesWithSuffocatingBlock(player, playerBox);
    }
}
