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
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.BlackoutEventDetails;

import java.lang.reflect.Type;

public class BlackoutEventDetailsSerializer implements JsonSerializer<BlackoutEventDetails>, JsonDeserializer<BlackoutEventDetails> {
    @Override
    public JsonElement serialize(BlackoutEventDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("duration", src.duration());
        return jsonObject;
    }

    @Override
    public BlackoutEventDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        long duration = jsonObject.get("duration").getAsLong();
        return new BlackoutEventDetails(duration);
    }
}