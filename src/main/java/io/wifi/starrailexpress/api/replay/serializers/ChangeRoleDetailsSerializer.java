package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ChangeRoleDetails;
import java.lang.reflect.Type;
import java.util.UUID;

public class ChangeRoleDetailsSerializer
        implements JsonSerializer<ChangeRoleDetails>, JsonDeserializer<ChangeRoleDetails> {
    @Override
    public JsonElement serialize(ChangeRoleDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("player", src.player().toString());
        jsonObject.addProperty("old_role", src.oldRole().toString());
        jsonObject.addProperty("new_role", src.newRole().toString());
        return jsonObject;
    }

    @Override
    public ChangeRoleDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID player = UUID.fromString(jsonObject.get("player").getAsString());
        String old_role = (jsonObject.get("old_role").getAsString());
        String new_role = (jsonObject.get("new_role").getAsString());
        return new ChangeRoleDetails(player, old_role, new_role);
    }
}