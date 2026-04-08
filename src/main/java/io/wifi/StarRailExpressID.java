package io.wifi;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class StarRailExpressID {
    // 验证版本号系统，强制客户端更新。
    public static final String modPacketVersion = "0.1.8";

    // ID
    public final static String MOD_ID = "starrailexpress";
    public final static String MOD_SHORT_ID = "sre";
    public final static String WATHE_MOD_ID = "wathe";
    public final static String TMM_MOD_ID = "trainmurdermystery";
    public final static String WIFI_MOD_ID = "wifi";
    public final static String HAIMAN_MOD_ID = "haiman";
    public final static String CANYUESAMA_MOD_ID = "canyuesama";
    public final static String MIFAN_MOD_ID = "mifan233";
    public final static String XIAOHEIHAND_MOD_ID = "xiao_hei_hand";
    public final static String BLACK_WHITE_BEAR_MOD_ID = "thef0rs4ken";

    public static @NotNull ResourceLocation shortId(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_SHORT_ID, name);
    }

    public static @NotNull ResourceLocation watheId(String name) {
        return ResourceLocation.fromNamespaceAndPath(WATHE_MOD_ID, name);
    }

    public static @NotNull ResourceLocation canyueId(String name) {
        return ResourceLocation.fromNamespaceAndPath(CANYUESAMA_MOD_ID, name);
    }

    public static @NotNull ResourceLocation wifiId(String name) {
        return ResourceLocation.fromNamespaceAndPath(WIFI_MOD_ID, name);
    }

    public static @NotNull ResourceLocation haimanId(String name) {
        return ResourceLocation.fromNamespaceAndPath(HAIMAN_MOD_ID, name);
    }

    public static @NotNull ResourceLocation thef0rs4kenId(String name) {
        return ResourceLocation.fromNamespaceAndPath(BLACK_WHITE_BEAR_MOD_ID, name);
    }

    public static @NotNull ResourceLocation xiaoheihandId(String name) {
        return ResourceLocation.fromNamespaceAndPath(XIAOHEIHAND_MOD_ID, name);
    }

    public static @NotNull ResourceLocation mifanId(String name) {
        return ResourceLocation.fromNamespaceAndPath(MIFAN_MOD_ID, name);
    }

    public static @NotNull ResourceLocation TMMId(String name) {
        return ResourceLocation.fromNamespaceAndPath(TMM_MOD_ID, name);
    }
}
