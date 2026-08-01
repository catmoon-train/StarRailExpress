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

package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ChangeRoleDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class ChangeRoleDetailsSerializer
        implements JsonSerializer<ChangeRoleDetails>, JsonDeserializer<ChangeRoleDetails> {
    @Override
    public JsonElement serialize(ChangeRoleDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("player", src.player().toString());
        jsonObject.addProperty("old_role", src.oldRole().toString());
        jsonObject.addProperty("new_role", src.newRole().toString());
        return jsonObject;
    }

    @Override
    public ChangeRoleDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID player = UUID.fromString(jsonObject.get("player").getAsString());
        String old_role = (jsonObject.get("old_role").getAsString());
        String new_role = (jsonObject.get("new_role").getAsString());
        return new ChangeRoleDetails(player, old_role, new_role);
    }
}