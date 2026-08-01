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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.CustomEventDetails;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Type;

public class CustomEventDetailsSerializer
        implements JsonSerializer<CustomEventDetails>, JsonDeserializer<CustomEventDetails> {
    @Override
    public JsonElement serialize(CustomEventDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        if (SRE.SERVER != null) {
            var provider = SRE.SERVER.registryAccess();
            if (provider != null) {
                try {
                    jsonObject.addProperty("message", Component.Serializer.toJson(src.Message(), provider));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                jsonObject.addProperty("message", "");
            }
        } else {
            jsonObject.addProperty("message", "");
        }

        return jsonObject;
    }

    @Override
    public CustomEventDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String data = jsonObject.get("message").getAsString();
        if (SRE.SERVER != null) {
            var provider = SRE.SERVER.registryAccess();
            if (provider != null) {
                return new CustomEventDetails(Component.Serializer.fromJson(data, provider));
            }
        }

        return new CustomEventDetails(null);
    }
}