package org.agmas.harpymodloader.modded_murder;



import io.wifi.starrailexpress.api.SRERole;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModdedWeights {

    public static Map<SRERole, HashMap<UUID, Integer>> roleRounds = new HashMap<>();
    public static Map<String,Float> getWeights(){
        return HarpyModLoaderConfig.HANDLER.instance().roleWeights;
    }
    public static float getRoleWeight(SRERole role){
        var customWeight = getWeights().get(role.identifier().toString());
        if (customWeight != null && customWeight > 0) {
            return customWeight;
        }
        // 返回默认权重
        return 1.0f;
    }
    public static void init() {}
}