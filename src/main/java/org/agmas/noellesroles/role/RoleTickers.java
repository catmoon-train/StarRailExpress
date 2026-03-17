package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.server.level.ServerPlayer;

public class RoleTickers {
    public static void oldmanTick(ServerPlayer player, SREGameWorldComponent gameWorldComponent){
        var pmc = SREPlayerTaskComponent.KEY.get(player);
        if (!(pmc.tasks.isEmpty())
            && pmc.tasks.getOrDefault(SREPlayerTaskComponent.Task.EXERCISE, null) != null) {
          if (pmc.tasks.get(
              SREPlayerTaskComponent.Task.EXERCISE) instanceof SREPlayerTaskComponent.ExerciseTask et) {
            et.timer = 0;
          }
        }
    }
}
