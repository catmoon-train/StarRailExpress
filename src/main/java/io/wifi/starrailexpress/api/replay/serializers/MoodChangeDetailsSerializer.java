package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.MoodChangeDetails;

import java.lang.reflect.Type;
import java.util.UUID;

public class MoodChangeDetailsSerializer implements JsonSerializer<MoodChangeDetails>, JsonDeserializer<MoodChangeDetails> {
    @Override
    public JsonElement serialize(MoodChangeDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("oldMood", src.oldMood());
        jsonObject.addProperty("newMood", src.newMood());
        return jsonObject;
    }

    @Override
    public MoodChangeDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        int oldMood = jsonObject.get("oldMood").getAsInt();
        int newMood = jsonObject.get("newMood").getAsInt();
        return new MoodChangeDetails(playerUuid, oldMood, newMood);
    }
}