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