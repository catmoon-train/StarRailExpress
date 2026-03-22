package io.wifi.starrailexpress.util;

import net.minecraft.world.phys.AABB;

import java.util.List;

public record Carriage(List<AABB> areas, String name) {
}