package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.StoreBuyDetails;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.UUID;

public class StoreBuyDetailsSerializer implements JsonSerializer<StoreBuyDetails>, JsonDeserializer<StoreBuyDetails> {
    @Override
    public JsonElement serialize(StoreBuyDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("itemId", src.itemId().toString());
        jsonObject.addProperty("cost", src.cost());
        return jsonObject;
    }

    @Override
    public StoreBuyDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        ResourceLocation itemId = ResourceLocation.parse(jsonObject.get("itemId").getAsString());
        int cost = jsonObject.get("cost").getAsInt();
        return new StoreBuyDetails(playerUuid, itemId, cost);
    }
}