package org.agmas.noellesroles.game.modes.fourthroom.shop;

public enum FourthRoomShopItem {
    SCORPION("scorpion"),
    HANDGUN("handgun"),
    POISON_MUSHROOM("poison_mushroom"),
    BULLETPROOF_VEST("bulletproof_vest"),
    TEST_STRIP("test_strip"),
    STICKY_NOTE("sticky_note");

    private final String id;

    FourthRoomShopItem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static FourthRoomShopItem byId(String id) {
        for (FourthRoomShopItem value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return null;
    }
}