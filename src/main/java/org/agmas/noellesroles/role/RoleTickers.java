package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.server.level.ServerPlayer;

public class RoleTickers {
    public static void oldmanTick(ServerPlayer player, SREGameWorldComponent gameWorldComponent){
        var pmc = SREPlayerMoodComponent.KEY.get(player);
        if (!(pmc.tasks.isEmpty())
            && pmc.tasks.getOrDefault(SREPlayerMoodComponent.Task.EXERCISE, null) != null) {
          if (pmc.tasks.get(
              SREPlayerMoodComponent.Task.EXERCISE) instanceof SREPlayerMoodComponent.ExerciseTask et) {
            et.timer = 0;
          }
        }
    }
}
