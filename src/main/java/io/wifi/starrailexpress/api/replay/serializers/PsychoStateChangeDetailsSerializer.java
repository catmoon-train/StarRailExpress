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
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PsychoStateChangeDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class PsychoStateChangeDetailsSerializer implements JsonSerializer<PsychoStateChangeDetails>, JsonDeserializer<PsychoStateChangeDetails> {
    @Override
    public JsonElement serialize(PsychoStateChangeDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("oldState", src.oldState());
        jsonObject.addProperty("newState", src.newState());
        return jsonObject;
    }

    @Override
    public PsychoStateChangeDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        int oldState = jsonObject.get("oldState").getAsInt();
        int newState = jsonObject.get("newState").getAsInt();
        return new PsychoStateChangeDetails(playerUuid, oldState, newState);
    }
}