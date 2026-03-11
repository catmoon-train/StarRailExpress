package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerPoisonedDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class PlayerPoisonedDetailsSerializer implements JsonSerializer<PlayerPoisonedDetails>, JsonDeserializer<PlayerPoisonedDetails> {
    @Override
    public JsonElement serialize(PlayerPoisonedDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("poisonerUuid", src.poisonerUuid().toString());
        jsonObject.addProperty("victimUuid", src.victimUuid().toString());
        return jsonObject;
    }

    @Override
    public PlayerPoisonedDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID poisonerUuid = UUID.fromString(jsonObject.get("poisonerUuid").getAsString());
        UUID victimUuid = UUID.fromString(jsonObject.get("victimUuid").getAsString());
        return new PlayerPoisonedDetails(poisonerUuid, victimUuid);
    }
}