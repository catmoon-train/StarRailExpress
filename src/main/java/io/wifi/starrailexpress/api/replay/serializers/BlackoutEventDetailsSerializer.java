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