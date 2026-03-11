package io.wifi.starrailexpress.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import walksy.crosshairaddons.CrosshairAddons;

public class CrosshairaddonsCompat {
    public static void onAttack(Entity target){
        if (FabricLoader.getInstance().isModLoaded("crosshairaddons")){
            final var addonStateManager = CrosshairAddons.getStateManager();
            if (target instanceof LivingEntity livingEntity) {
                addonStateManager.onAttackEntity(livingEntity);
            }
        }
    }
    public static void arrowHit(){
        if (FabricLoader.getInstance().isModLoaded("crosshairaddons")){
            final var addonStateManager = CrosshairAddons.getStateManager();
            addonStateManager.onArrowHit();
        }
    }
}
