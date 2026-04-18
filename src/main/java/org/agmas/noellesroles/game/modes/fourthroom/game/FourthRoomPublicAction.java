package org.agmas.noellesroles.game.modes.fourthroom.game;

import net.minecraft.nbt.CompoundTag;

public final class FourthRoomPublicAction {
    public int sequence;
    public long tick;
    public String category = "system";
    public String actorName = "";
    public String verb = "";
    public String subject = "";
    public String targetName = "";
    public String detail = "";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Sequence", sequence);
        tag.putLong("Tick", tick);
        tag.putString("Category", category == null ? "system" : category);
        tag.putString("ActorName", actorName == null ? "" : actorName);
        tag.putString("Verb", verb == null ? "" : verb);
        tag.putString("Subject", subject == null ? "" : subject);
        tag.putString("TargetName", targetName == null ? "" : targetName);
        tag.putString("Detail", detail == null ? "" : detail);
        return tag;
    }

    public static FourthRoomPublicAction load(CompoundTag tag) {
        FourthRoomPublicAction action = new FourthRoomPublicAction();
        action.sequence = tag.getInt("Sequence");
        action.tick = tag.getLong("Tick");
        action.category = tag.getString("Category");
        action.actorName = tag.getString("ActorName");
        action.verb = tag.getString("Verb");
        action.subject = tag.getString("Subject");
        action.targetName = tag.getString("TargetName");
        action.detail = tag.getString("Detail");
        return action;
    }
}