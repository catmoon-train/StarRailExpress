package net.exmo.sre.sixtyseconds.content.block;

import net.minecraft.util.StringRepresentable;

/** 避难所门的用途（blockstate 属性）：搜索 / 事件 / 拜访。 */
public enum DoorPurpose implements StringRepresentable {
    SEARCH,
    EVENT,
    VISIT;

    public DoorPurpose next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DoorPurpose byOrdinal(int ordinal) {
        DoorPurpose[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
