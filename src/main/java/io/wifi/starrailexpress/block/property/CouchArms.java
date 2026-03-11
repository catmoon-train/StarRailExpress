package io.wifi.starrailexpress.block.property;

import net.minecraft.util.StringRepresentable;

public enum CouchArms implements StringRepresentable {
    LEFT("left"),
    RIGHT("right"),
    SINGLE("single"),
    NO_ARMS("no_arms");

    private final String name;

    CouchArms(String name) {
        this.name = name;
    }

    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
