package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.DoorActionDetails;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;
import java.util.UUID;

public class DoorActionDetailsSerializer implements JsonSerializer<DoorActionDetails>, JsonDeserializer<DoorActionDetails> {
    @Override
    public JsonElement serialize(DoorActionDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("doorPosX", src.doorPos().getX());
        jsonObject.addProperty("doorPosY", src.doorPos().getY());
        jsonObject.addProperty("doorPosZ", src.doorPos().getZ());
        jsonObject.addProperty("success", src.success());
        return jsonObject;
    }

    @Override
    public DoorActionDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        int doorPosX = jsonObject.get("doorPosX").getAsInt();
        int doorPosY = jsonObject.get("doorPosY").getAsInt();
        int doorPosZ = jsonObject.get("doorPosZ").getAsInt();
        BlockPos doorPos = new BlockPos(doorPosX, doorPosY, doorPosZ);
        boolean success = jsonObject.get("success").getAsBoolean();
        return new DoorActionDetails(playerUuid, doorPos, success);
    }
}