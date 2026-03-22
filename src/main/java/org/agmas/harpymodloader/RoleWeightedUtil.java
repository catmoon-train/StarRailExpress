package org.agmas.harpymodloader;

import io.wifi.starrailexpress.game.StarRailMurderGameMode.RoleInstant;

import java.util.Map;
import java.util.Random;

public class RoleWeightedUtil extends WeightedUtil<RoleInstant> {
    public RoleWeightedUtil(Map<RoleInstant, Float> weights, Random random) {
        super(weights, random);
    }

    public RoleWeightedUtil(Map<RoleInstant, Float> weights) {
        super(weights);
    }

}