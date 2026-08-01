/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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