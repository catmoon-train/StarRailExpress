/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.utils.lottery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.agmas.noellesroles.Noellesroles;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 卡池配置文件解析器
 */
public class LotteryPoolsConfigParser {
    /** 从文件解析配置 */
    public static LotteryPoolsConfig parse(Path configPath) {
        if (!Files.exists(configPath)) {
            Noellesroles.LOGGER.warn("[LootSys] Lottery Pools Config file is not found: {}", configPath);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            return GSON.fromJson(reader, LotteryPoolsConfig.class);
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[LootSys] Failed to parse Lottery Pools config : {}", configPath, e);
            return null;
        }
    }

    public static LotteryPoolsConfig parse(String configJson) {
        try {
            return GSON.fromJson(configJson, LotteryPoolsConfig.class);
        } catch (Exception e) {
            Noellesroles.LOGGER.error("[LootSys] Failed to parse Lottery Pools config JSON string", e);
            return null;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
}

// import com.google.gson.*;
//
// import java.lang.reflect.Type;
// import java.util.ArrayList;
// import java.util.List;
/// **
// * 自定义卡池配置文件解析器
// * <p>
// * NOTE:
// * 由于目前卡池配置文件结构简单，可以使用GSON的自动解析，这里的解析器作为解析模板参考，如果需要自定义解析可以在此完成
// * </p>
// */
// public class LotteryPoolConfigDeserializer implements
// JsonDeserializer<LotteryPoolsConfig.PoolConfig> {
// @Override
// public LotteryPoolsConfig.PoolConfig deserialize(JsonElement json, Type
// typeOfT, JsonDeserializationContext context) throws JsonParseException {
// JsonObject jsonObject = json.getAsJsonObject();
// LotteryPoolsConfig.PoolConfig poolConfig = new
// LotteryPoolsConfig.PoolConfig();
// // 设置基本属性
// poolConfig.setName(jsonObject.get("PoolName").getAsString());
// poolConfig.setType(jsonObject.get("PoolType").getAsString());
// // 解析品质概率
// if(jsonObject.has("Probability"))
// {
// List<Double> probabilities = new ArrayList<>();
// JsonArray proArray = jsonObject.getAsJsonArray("Probability");
// for(JsonElement element : proArray)
// {
// probabilities.add(element.getAsDouble());
// }
// poolConfig.setProbabilities(probabilities);
// }
// // 解析品质项目列表
// if(jsonObject.has("QualityList"))
// {
// JsonArray qualityArray = jsonObject.getAsJsonArray("QualityList");
// List<List<String>> qualityList = new ArrayList<>();
// for(JsonElement element : qualityArray)
// {
// List<String> qualityListItem = new ArrayList<>();
// JsonArray qualityListItemArray = element.getAsJsonArray();
// for(JsonElement itemElement : qualityListItemArray)
// {
// qualityListItem.add(itemElement.getAsString());
// }
// qualityList.add(qualityListItem);
// }
// poolConfig.setQualityListConfigs(qualityList);
// }
// return poolConfig;
// }
// }
