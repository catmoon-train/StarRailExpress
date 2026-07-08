package io.wifi.utils.cca;

import org.agmas.noellesroles.init.ModEffects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class CCAManagerClient {

    public static boolean shouldBlockEntityCCAClientTick(Entity entity) {
        if (entity instanceof Player sp) {
            if (sp.hasEffect(ModEffects.SKILL_FREEZED)) {
                return true;
            }
        }
        return false;
    }
}
