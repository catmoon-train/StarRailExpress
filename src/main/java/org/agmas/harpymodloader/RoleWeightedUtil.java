package org.agmas.harpymodloader;

import io.wifi.starrailexpress.game.utils.RoleInstance;

import java.util.Map;
import java.util.Random;

public class RoleWeightedUtil extends WeightedUtil<RoleInstance> {
    public RoleWeightedUtil(Map<RoleInstance, Float> weights, Random random) {
        super(weights, random);
    }

    public RoleWeightedUtil(Map<RoleInstance, Float> weights) {
        super(weights);
    }

}