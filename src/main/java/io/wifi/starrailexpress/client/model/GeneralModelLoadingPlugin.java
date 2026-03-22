package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.item.SkinableItem;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;

import java.util.HashMap;

public class GeneralModelLoadingPlugin implements ModelLoadingPlugin {

    public static final HashMap<String, ModelResourceLocation> MODEL_IDS = new HashMap<>();
    static {
        for (Item skinnableitem : TMMItems.SkinableItem) {
            if (skinnableitem instanceof SkinableItem it) {
                String skinId = it.getItemSkinType();
                var model = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(it));
                MODEL_IDS.putIfAbsent(skinId, model);
            }
        }
    }

    public static ResourceLocation getModelLocation(String itemType, String skin, Variant variant) {
        var MODEL_ID = MODEL_IDS.get(itemType);
        if (MODEL_ID == null) {
            return null;
        }
        if (skin == "default") {
            return MODEL_ID.id().withPath(path -> "item/%s".formatted(MODEL_ID.id().getPath()));
        }
        var skinPart = "%s".formatted(skin);
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return SRE.id("item/skins/%s/%s%s".formatted(MODEL_ID.id().getPath(), skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // make sure all models get loaded
        for (var entry : SkinManager.getSkins().entrySet()) {
            for (SkinManager.Skin skin : entry.getValue().values()) {
                for (Variant variant : Variant.values()) {
                    pluginContext.addModels(getModelLocation(entry.getKey(), skin.getName(), variant));
                }
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            if (context.topLevelId() != null) {
                var item = BuiltInRegistries.ITEM.get(context.topLevelId().id());
                if (item instanceof SkinableItem it) {
                    var itemName = it.getItemSkinType();
                    return new GeneralModel(itemName, context.topLevelId(),unbakedModel);
                }
            }

            // var mid = context.topLevelId();
            // if (MODEL_IDS.values().contains(mid)) {
            // return new GeneralModel(MODEL_IDS_MAPPINGS.get(mid), unbakedModel);
            // }
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
