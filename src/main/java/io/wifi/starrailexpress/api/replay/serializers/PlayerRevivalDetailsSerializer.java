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
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerRevivalDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class PlayerRevivalDetailsSerializer
        implements JsonSerializer<PlayerRevivalDetails>, JsonDeserializer<PlayerRevivalDetails> {
    @Override
    public JsonElement serialize(PlayerRevivalDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("player", src.player().toString());
        jsonObject.addProperty("role", src.role().toString());

        return jsonObject;
    }

    @Override
    public PlayerRevivalDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID player = UUID.fromString(jsonObject.get("player").getAsString());
        String old_role = (jsonObject.get("role").getAsString());
        return new PlayerRevivalDetails(player, old_role);
    }
}