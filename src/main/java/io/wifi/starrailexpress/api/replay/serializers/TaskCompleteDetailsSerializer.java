package io.wifi.starrailexpress.api.replay.serializers;

import com.google.gson.*;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.TaskCompleteDetails;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.UUID;

public class TaskCompleteDetailsSerializer implements JsonSerializer<TaskCompleteDetails>, JsonDeserializer<TaskCompleteDetails> {
    @Override
    public JsonElement serialize(TaskCompleteDetails src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("playerUuid", src.playerUuid().toString());
        jsonObject.addProperty("taskId", src.taskId().toString());
        return jsonObject;
    }

    @Override
    public TaskCompleteDetails deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        UUID playerUuid = UUID.fromString(jsonObject.get("playerUuid").getAsString());
        ResourceLocation taskId = ResourceLocation.parse(jsonObject.get("taskId").getAsString());
        return new TaskCompleteDetails(playerUuid, taskId);
    }
}