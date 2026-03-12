package org.agmas.harpymodloader;

import java.util.*;

import io.wifi.starrailexpress.game.StarRailMurderGameMode.RoleInstant;

public class RoleWeightedUtil extends WeightedUtil<RoleInstant> {
    public RoleWeightedUtil(Map<RoleInstant, Float> weights, Random random) {
        super(weights, random);
    }

    public RoleWeightedUtil(Map<RoleInstant, Float> weights) {
        super(weights);
    }

}