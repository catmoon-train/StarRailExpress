package org.agmas.harpymodloader.modded_murder;



import io.wifi.starrailexpress.api.Role;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public class ModdedWeights {

    public static Map<Role, HashMap<UUID, Integer>> roleRounds = new HashMap<>();
    public static Map<ResourceLocation,Float> getWeights(){
        return HarpyModLoaderConfig.HANDLER.instance().roleWeights;
    }
    public static float getRoleWeight(Role role){
        var customWeight = getWeights().get(role.identifier());
        if (customWeight != null && customWeight > 0) {
            return customWeight;
        }
        // 返回默认权重
        return 1.0f;
    }
    public static void init() {}
}