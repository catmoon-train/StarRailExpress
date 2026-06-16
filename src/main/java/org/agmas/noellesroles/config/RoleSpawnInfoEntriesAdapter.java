package org.agmas.noellesroles.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.HashMap;
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
        JsonObject result = new JsonObject();
        result.addProperty("type", src.type);
        JsonArray array = new JsonArray();
        for (Map.Entry<ResourceLocation, SpawnInfo> entry : src.maps.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("key", entry.getKey().toString());
            obj.add("value", context.serialize(entry.getValue()));
            array.add(obj);
        }
        result.add("entries", array);
        return result;
    }

    @Override
    public RoleSpawnInfoEntries deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonObject()) {
            return new RoleSpawnInfoEntries();
        }

        JsonObject root = json.getAsJsonObject();

        // 读取 type
        int type = 0;
        JsonElement typeElement = root.get("type");
        if (typeElement != null && typeElement.isJsonPrimitive()) {
            type = typeElement.getAsInt();
        }

        // 根据 type 获取默认映射
        RoleSpawnInfoEntries defaultObj;
        if (type == 1) {
            defaultObj = RoleSpawnInfoEntries.createDefaultRoleInfo();
        } else if (type == 2) {
            defaultObj = RoleSpawnInfoEntries.createDefaultModifierInfo();
        } else {
            // 未知 type，返回空对象
            return null;
        }

        // 复制默认映射作为基础
        HashMap<ResourceLocation, SpawnInfo> resultMap = new HashMap<>(defaultObj.maps);

        // 解析 entries 数组
        JsonElement entriesElement = root.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            // 没有 entries，直接返回默认映射
            RoleSpawnInfoEntries result = new RoleSpawnInfoEntries();
            result.type = type;
            result.maps = resultMap;
            return result;
        }

        JsonArray array = entriesElement.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject())
                continue;
            JsonObject obj = element.getAsJsonObject();

            // 读取 key
            JsonElement keyElement = obj.get("key");
            if (keyElement == null || !keyElement.isJsonPrimitive())
                continue;
            String keyStr = keyElement.getAsString();
            ResourceLocation rl = ResourceLocation.tryParse(keyStr);
            if (rl == null)
                continue;

            // 只在默认映射中存在该 key 时才覆盖
            if (resultMap.containsKey(rl)) {
                JsonElement valueElement = obj.get("value");
                if (valueElement == null)
                    continue;
                SpawnInfo spawnInfo = context.deserialize(valueElement, SPAWN_INFO_TYPE);
                resultMap.put(rl, spawnInfo); // 覆盖默认值
            }
            // 否则直接忽略（不添加）
        }

        // 构建最终结果
        RoleSpawnInfoEntries result = new RoleSpawnInfoEntries();
        result.type = type;
        result.maps = resultMap;
        return result;
    }
}