package io.wifi.starrailexpress.util;

import java.util.List;
import net.minecraft.world.phys.AABB;

public record Carriage(List<AABB> areas, String name) {
}