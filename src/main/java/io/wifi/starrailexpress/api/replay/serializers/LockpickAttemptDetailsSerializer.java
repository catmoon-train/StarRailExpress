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
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.LockpickAttemptDetails;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;
import java.util.UUID;

public class LockpickAttemptDetailsSerializer implements JsonSerializer<LockpickAttemptDetails>, JsonDeserializer<LockpickAttemptDetails> {
    @Override
    public JsonElement serialize(LockpickAttemptDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("doorPosX", src.doorPos().getX());
        jsonObject.addProperty("doorPosY", src.doorPos().getY());
        jsonObject.addProperty("doorPosZ", src.doorPos().getZ());
        jsonObject.addProperty("success", src.success());
        return jsonObject;
    }

    @Override
    public LockpickAttemptDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        int doorPosX = jsonObject.get("doorPosX").getAsInt();
        int doorPosY = jsonObject.get("doorPosY").getAsInt();
        int doorPosZ = jsonObject.get("doorPosZ").getAsInt();
        BlockPos doorPos = new BlockPos(doorPosX, doorPosY, doorPosZ);
        boolean success = jsonObject.get("success").getAsBoolean();
        return new LockpickAttemptDetails(playerUuid, doorPos, success);
    }
}