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
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.GrenadeThrownDetails;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;
import java.util.UUID;

public class GrenadeThrownDetailsSerializer implements JsonSerializer<GrenadeThrownDetails>, JsonDeserializer<GrenadeThrownDetails> {
    @Override
    public JsonElement serialize(GrenadeThrownDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("positionX", src.position().getX());
        jsonObject.addProperty("positionY", src.position().getY());
        jsonObject.addProperty("positionZ", src.position().getZ());
        return jsonObject;
    }

    @Override
    public GrenadeThrownDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        int positionX = jsonObject.get("positionX").getAsInt();
        int positionY = jsonObject.get("positionY").getAsInt();
        int positionZ = jsonObject.get("positionZ").getAsInt();
        BlockPos position = new BlockPos(positionX, positionY, positionZ);
        return new GrenadeThrownDetails(playerUuid, position);
    }
}