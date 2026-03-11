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