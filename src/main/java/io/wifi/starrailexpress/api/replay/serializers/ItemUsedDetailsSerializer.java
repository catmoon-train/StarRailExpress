package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ItemUsedDetails;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.UUID;

public class ItemUsedDetailsSerializer implements JsonSerializer<ItemUsedDetails>, JsonDeserializer<ItemUsedDetails> {
    @Override
    public JsonElement serialize(ItemUsedDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("itemId", src.itemId().toString());
        return jsonObject;
    }

    @Override
    public ItemUsedDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        ResourceLocation itemId = ResourceLocation.parse(jsonObject.get("itemId").getAsString());
        return new ItemUsedDetails(playerUuid, itemId);
    }
}