package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerRevivalDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class PlayerRevivalDetailsSerializer
        implements JsonSerializer<PlayerRevivalDetails>, JsonDeserializer<PlayerRevivalDetails> {
    @Override
    public JsonElement serialize(PlayerRevivalDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("player", src.player().toString());
        jsonObject.addProperty("role", src.Role().toString());

        return jsonObject;
    }

    @Override
    public PlayerRevivalDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID player = UUID.fromString(jsonObject.get("player").getAsString());
        String old_role = (jsonObject.get("role").getAsString());
        return new PlayerRevivalDetails(player, old_role);
    }
}