package org.agmas.noellesroles.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.Map;

import org.agmas.noellesroles.config.NoellesRolesConfig.RoleSpawnInfoEntries;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;

public class RoleSpawnInfoEntriesAdapter
        implements JsonSerializer<RoleSpawnInfoEntries>,
        JsonDeserializer<RoleSpawnInfoEntries> {

    private static final Type SPAWN_INFO_TYPE = new TypeToken<SpawnInfo>() {
    }.getType();

    @Override
    public JsonElement serialize(RoleSpawnInfoEntries src, Type typeOfSrc,
            JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        for (Map.Entry<ResourceLocation, SpawnInfo> entry : src.maps.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("key", entry.getKey().toString());
            obj.add("value", context.serialize(entry.getValue()));
            array.add(obj);
        }
        return array;
    }

    @Override
    public RoleSpawnInfoEntries deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context)
            throws JsonParseException {
        RoleSpawnInfoEntries result = new RoleSpawnInfoEntries();
        if (!json.isJsonArray()) {
            return result;
        }
        JsonArray array = json.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject())
                continue;
            JsonObject obj = element.getAsJsonObject();

            // 读取 key
            JsonElement keyElement = obj.get("key");
            if (keyElement == null || !keyElement.isJsonPrimitive())
                continue;
            ResourceLocation rl = ResourceLocation.tryParse(keyElement.getAsString());
            if (rl == null)
                continue; // 无法解析的 key 直接忽略

            // 读取 value
            JsonElement valueElement = obj.get("value");
            if (valueElement == null)
                continue;
            SpawnInfo spawnInfo = context.deserialize(valueElement, SPAWN_INFO_TYPE);
            result.maps.put(rl, spawnInfo);
        }
        return result;
    }
}