package io.wifi.utils.cca;

import org.agmas.noellesroles.init.ModEffects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CCAManager {
    public static boolean shouldBlockEntityCCAServerTick(Entity entity){
        if(entity instanceof ServerPlayer sp){
            if(sp.hasEffect(ModEffects.SKILL_FREEZED)){
                return true;
            }
        }
        return false;
    }
}
