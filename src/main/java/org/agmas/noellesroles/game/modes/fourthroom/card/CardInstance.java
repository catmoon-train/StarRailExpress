package org.agmas.noellesroles.game.modes.fourthroom.card;

import net.minecraft.nbt.CompoundTag;

public record CardInstance(String cardId, boolean gold) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CardId", cardId);
        tag.putBoolean("Gold", gold);
        return tag;
    }

    public static CardInstance load(CompoundTag tag) {
        return new CardInstance(tag.getString("CardId"), tag.getBoolean("Gold"));
    }
}