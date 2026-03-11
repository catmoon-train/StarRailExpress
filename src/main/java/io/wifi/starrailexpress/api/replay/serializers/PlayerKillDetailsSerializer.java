package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerKillDetails;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.UUID;

public class PlayerKillDetailsSerializer implements JsonSerializer<PlayerKillDetails>, JsonDeserializer<PlayerKillDetails> {
    @Override
    public JsonElement serialize(PlayerKillDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("killerUuid", src.killerUuid().toString());
        jsonObject.addProperty("victimUuid", src.victimUuid().toString());
        jsonObject.addProperty("deathReason", src.deathReason().toString());
        return jsonObject;
    }

    @Override
    public PlayerKillDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID killerUuid = UUID.fromString(jsonObject.get("killerUuid").getAsString());
        UUID victimUuid = UUID.fromString(jsonObject.get("victimUuid").getAsString());
        ResourceLocation deathReason = ResourceLocation.parse(jsonObject.get("deathReason").getAsString());
        return new PlayerKillDetails(killerUuid, victimUuid, deathReason);
    }
}