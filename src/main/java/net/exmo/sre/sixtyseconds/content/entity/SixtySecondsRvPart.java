package net.exmo.sre.sixtyseconds.content.entity;

import java.util.Locale;

/** 房车可装配的二十种模块；物品、服务端状态和管理界面统一使用这一份定义。 */
public enum SixtySecondsRvPart {
    AUXILIARY_TANK,
    RESERVE_TANK,
    FUEL_INJECTOR,
    ECONOMY_CARBURETOR,
    ALL_TERRAIN_TIRES,
    REINFORCED_SUSPENSION,
    WINCH,
    SKID_PLATE,
    ARMORED_PLATING,
    REINFORCED_FRAME,
    EMERGENCY_ARMOR,
    TOOL_BENCH,
    FIELD_REPAIR_KIT,
    MEDICAL_BAY,
    SOLAR_PANEL,
    ROOF_LIGHTS,
    LONG_RANGE_RADIO,
    NAVIGATION_ARRAY,
    SCOUTING_RADAR,
    EXTRA_SEATS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
