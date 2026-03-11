package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ArmorBreakDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class ArmorBreakDetailsSerializer implements JsonSerializer<ArmorBreakDetails>, JsonDeserializer<ArmorBreakDetails> {
    @Override
    public JsonElement serialize(ArmorBreakDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        return jsonObject;
    }

    @Override
    public ArmorBreakDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        return new ArmorBreakDetails(playerUuid);
    }
}