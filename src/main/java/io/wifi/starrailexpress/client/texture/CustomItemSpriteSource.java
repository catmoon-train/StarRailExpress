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

package io.wifi.starrailexpress.client.texture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.render.item.CustomItemRenderer;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import io.wifi.starrailexpress.mixin.client.texture.SpriteSourcesAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 自定义列车物品的<b>图集来源</b>：把配置里用到的贴图动态注册进图集。
 *
 * <p>
 * 注册进图集后，这些贴图才能被「不走我们自己渲染器」的场合使用：
 * <ul>
 * <li><b>食用 / 破碎粒子</b>（{@code BreakingItemParticle} 直接采样方块图集）——
 * 否则就是「材质丢失」的紫黑图标；</li>
 * <li><b>「模型地址」指向的模型自带的贴图</b> —— 否则 MODEL 模式直接画模型时贴图是缺失的。</li>
 * </ul>
 *
 * <p>
 * 通过 {@link SpriteSourcesAccessor} 把本类型登记进 {@link SpriteSources} 的类型表
 * （原版只在静态初始化里登记它自己的五种，模组类型需要走这一步），再由
 * {@code assets/starrailexpress/atlases/blocks.json} 引用。
 *
 * <p>
 * 图集拼接发生在<b>资源加载阶段</b>：改完贴图 / 模型地址后要重载一次资源（F3+T）才会生效，
 * 与「模型地址」的行为一致。
 */
@Environment(EnvType.CLIENT)
public class CustomItemSpriteSource implements SpriteSource {

    /** 无字段：所有贴图都在 {@link #run} 里按当前配置动态收集。 */
    public static final MapCodec<CustomItemSpriteSource> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.point(new CustomItemSpriteSource()));

    private static SpriteSourceType type;

    /** 客户端初始化时调用：把来源类型登记进 {@link SpriteSources}（必须在资源加载前）。 */
    public static void register() {
        SpriteSourceType sourceType = new SpriteSourceType(CODEC);
        SpriteSourcesAccessor.sre$getTypes().put(SRE.id("custom_item_textures"), sourceType);
        type = sourceType;
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        for (ResourceLocation spriteId : collectSpriteIds(resourceManager)) {
            Optional<Resource> resource = resourceManager.getResource(TEXTURE_ID_CONVERTER.idToFile(spriteId));
            resource.ifPresent(value -> output.add(spriteId, value));
        }
    }

    @Override
    public SpriteSourceType type() {
        return type;
    }

    /**
     * 收集所有自定义物品配置里用到的贴图（sprite id 形式，如 {@code mypack:item/my_gun}）。
     */
    static List<ResourceLocation> collectSpriteIds(ResourceManager resourceManager) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (CustomItemData data : CustomItemLoader.getAllData()) {
            if (data == null) {
                continue;
            }
            // ① 资源包贴图 / 动态贴图帧
            addTexture(ids, data.packTexturePath);
            for (String frame : data.animatedFramePaths()) {
                addTexture(ids, frame);
            }
            // ② 「模型地址」指向的模型自带的贴图（沿 parent 链解析）
            collectModelTextures(resourceManager, ids, data.modelPath);
            for (String modelPath : data.animatedModelFramePaths()) {
                collectModelTextures(resourceManager, ids, modelPath);
            }
        }
        return List.copyOf(ids);
    }

    /** 把配置里的一张贴图地址换算成 sprite id（{@code resolvePackTexture} 解析成功才收录）。 */
    private static void addTexture(Set<ResourceLocation> ids, String configured) {
        ResourceLocation file = CustomItemRenderer.resolvePackTexture(configured);
        if (file != null) {
            ids.add(TEXTURE_ID_CONVERTER.fileToId(file));
        }
    }

    /**
     * 解析「模型地址」指向的模型 json（含 {@code parent} 链）里引用到的贴图。
     *
     * <p>
     * 模型里写的是 {@code "#ref"} 引用，这里按 parent 链合并各层的 {@code textures} 表后再解引用；
     * 解析失败（贴图地址写错等）静默跳过，不影响其它贴图。
     */
    private static void collectModelTextures(ResourceManager resourceManager, Set<ResourceLocation> ids,
            String modelPath) {
        ResourceLocation modelId = CustomItemData.resolveModelId(modelPath);
        if (modelId == null) {
            return;
        }
        Map<String, String> slots = new HashMap<>();
        Set<ResourceLocation> visited = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        queue.add(modelId);
        while (!queue.isEmpty() && visited.size() < 16) {
            ResourceLocation id = queue.poll();
            if (id == null || !visited.add(id)) {
                continue;
            }
            ResourceLocation file = ResourceLocation.tryBuild(id.getNamespace(), "models/" + id.getPath() + ".json");
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isEmpty()) {
                continue;
            }
            JsonObject json;
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                continue;
            }
            if (json.has("parent") && json.get("parent").isJsonPrimitive()) {
                ResourceLocation parent = ResourceLocation.tryParse(json.get("parent").getAsString());
                if (parent != null) {
                    queue.add(parent);
                }
            }
            if (json.has("textures") && json.get("textures").isJsonObject()) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : json.getAsJsonObject("textures")
                        .entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        slots.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        for (String key : slots.keySet()) {
            ResourceLocation id = parseTextureId(resolveSlot(slots, key, 0));
            if (id != null) {
                ids.add(id);
            }
        }
    }

    /** 解析模型贴图槽位：{@code "#ref"} 继续往下查（最多 8 层防环）。 */
    private static String resolveSlot(Map<String, String> slots, String key, int depth) {
        String value = slots.get(key);
        if (value == null || !value.startsWith("#")) {
            return value;
        }
        if (depth >= 8) {
            return null;
        }
        return resolveSlot(slots, value.substring(1), depth + 1);
    }

    /** 模型贴图槽位值（{@code ns:path}）→ sprite id；{@code "#ref"} / 写法非法返回 null。 */
    private static ResourceLocation parseTextureId(String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("#")) {
            return null;
        }
        String trimmed = raw.trim();
        ResourceLocation location = ResourceLocation.tryParse(trimmed);
        if (location == null) {
            location = ResourceLocation.tryBuild("minecraft", trimmed);
        }
        return location;
    }
}
