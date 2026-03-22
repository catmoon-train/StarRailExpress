package io.wifi;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class StarRailExpressID {
    public final static String MOD_ID = "starrailexpress";
    public final static String WATHE_MOD_ID = "wathe";
    public final static String TMM_MOD_ID = "trainmurdermystery";
    public static final String modPacketVersion = "0.1.5";

    public static @NotNull ResourceLocation watheId(String name) {
        return ResourceLocation.fromNamespaceAndPath(WATHE_MOD_ID, name);
    }
    public static @NotNull ResourceLocation TMMId(String name) {
        return ResourceLocation.fromNamespaceAndPath(TMM_MOD_ID, name);
    }
}
