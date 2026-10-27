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

package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeneralModelLoadingPlugin implements ModelLoadingPlugin {

    /** 皮肤类型 → 主基准模型：决定该类型皮肤模型在 {@code models/item/skins/<类型>/} 下的路径 */
    public static final HashMap<String, ModelResourceLocation> MODEL_IDS = new HashMap<>();

    /**
     * 皮肤类型 → 所有持有该类型皮肤的物品模型。
     * <p>
     * 同一皮肤类型可能被多个物品共用（例如乘务员钥匙 {@code noellesroles:master_key_p}
     * 与列车钥匙共用 {@code key} 皮肤），这些物品各有自己的物品模型文件，
     * 必须全部接管，否则只有主基准物品能换肤。
     */
    private static final Map<String, Set<ModelResourceLocation>> BASE_MODELS = new HashMap<>();

    /**
     * 分阶段物品的模型后缀。
     * <p>
     * 原版弓/弩的拉弓、装填状态是彼此独立的模型（{@code minecraft:item/bow_pulling_0} 等），
     * 由原版 {@code overrides} 按 pulling/pull/charged/arrow/firework 条件切换。
     * 因为切换发生在 {@code ItemOverrides} 层，皮肤也必须为每个阶段各准备一份模型，
     * 这里列出需要额外处理的阶段后缀；没有后缀（空串）即物品本身。
     */
    private static final Map<String, List<String>> STAGE_SUFFIXES = Map.of(
            ItemSkinManager.SkinTypes.BOW,
            List.of("", "_pulling_0", "_pulling_1", "_pulling_2"),
            ItemSkinManager.SkinTypes.CROSSBOW,
            List.of("", "_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework"));

    static {
        for (Item skinnableitem : TMMItems.SkinableItem) {
            String skinId = ItemSkinManager.getItemSkinType(skinnableitem);
            if (skinId != null) {
                var model = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(skinnableitem));
                MODEL_IDS.putIfAbsent(skinId, model);
            }
        }
        // 原版物品（弓、弩）同样需要一个「基准模型」用于拼皮肤模型路径
        for (var entry : ItemSkinManager.getVanillaSkinItems().entrySet()) {
            MODEL_IDS.putIfAbsent(entry.getValue(),
                    ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(entry.getKey())));
        }
        // 扫描全部物品：凡是登记过皮肤类型的物品都要接管模型，
        // 这样与主基准共用皮肤类型的物品（如乘务员钥匙 master_key_p）也能换肤。
        for (Item item : BuiltInRegistries.ITEM) {
            String skinType = ItemSkinManager.getItemSkinType(item);
            if (skinType == null) {
                continue;
            }
            BASE_MODELS.computeIfAbsent(skinType, k -> new LinkedHashSet<>())
                    .add(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item)));
        }
        // 主基准若不在上面的扫描结果中（例如仅登记在页签列表里），一并补上
        for (var entry : MODEL_IDS.entrySet()) {
            BASE_MODELS.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).add(entry.getValue());
        }
    }

    /** 该皮肤类型是否带分阶段模型（弓、弩）。 */
    public static boolean isStaged(String itemType) {
        return STAGE_SUFFIXES.containsKey(itemType);
    }

    public static ResourceLocation getModelLocation(String itemType, String skin, Variant variant) {
        return getModelLocation(itemType, skin, variant, "");
    }

    /**
     * @param stageSuffix 阶段后缀，如 {@code ""}、{@code "_pulling_0"}、{@code "_arrow"}
     */
    public static ResourceLocation getModelLocation(String itemType, String skin, Variant variant, String stageSuffix) {
        var MODEL_ID = MODEL_IDS.get(itemType);
        if (MODEL_ID == null) {
            return null;
        }
        String suffix = stageSuffix == null ? "" : stageSuffix;
        if ("default".equals(skin)) {
            // 未装备皮肤 → 原版模型（分阶段时同样带上阶段后缀）
            return MODEL_ID.id().withPath(path -> "item/%s%s".formatted(path, suffix));
        }
        // 分阶段物品只用阶段区分模型，不再区分手持/GUI
        var variantPart = (variant == Variant.DEFAULT || isStaged(itemType)) ? ""
                : "_%s".formatted(variant.getSerializedName());

        return SRE.id("item/skins/%s/%s%s%s".formatted(MODEL_ID.id().getPath(), skin, suffix, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // make sure all models get loaded
        for (var entry : ItemSkinManager.getSkins().entrySet()) {
            List<String> stages = STAGE_SUFFIXES.getOrDefault(entry.getKey(), List.of(""));
            for (ItemSkinManager.Skin skin : entry.getValue().values()) {
                for (Variant variant : Variant.values()) {
                    for (String stage : stages) {
                        ResourceLocation location = getModelLocation(entry.getKey(), skin.getName(), variant, stage);
                        if (location == null) {
                            continue;
                        }
                        pluginContext.addModels(location);
                    }
                }
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            if (context.topLevelId() != null) {
                ResourceLocation id = context.topLevelId().id();
                String path = id.getPath();
                if (path.startsWith("item/")) {
                    path = path.substring("item/".length());
                }
                for (var entry : BASE_MODELS.entrySet()) {
                    String itemType = entry.getKey();
                    for (ModelResourceLocation base : entry.getValue()) {
                        if (!base.id().getNamespace().equals(id.getNamespace())) {
                            continue;
                        }
                        String basePath = base.id().getPath();
                        if (!path.startsWith(basePath)) {
                            continue;
                        }
                        String suffix = path.substring(basePath.length());
                        if (!suffix.isEmpty()) {
                            // 只接受该类型明确登记过的阶段后缀，避免误匹配同前缀的模型
                            List<String> stages = STAGE_SUFFIXES.get(itemType);
                            if (stages == null || !stages.contains(suffix)) {
                                continue;
                            }
                        }
                        return new GeneralModel(itemType, base, suffix, unbakedModel);
                    }
                }
            }
            return unbakedModel;
        });
    }

    public enum Variant implements StringRepresentable {
        DEFAULT("default"),
        IN_HAND("in_hand");

        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
