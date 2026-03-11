package io.wifi.starrailexpress.client.model;


import io.wifi.starrailexpress.item.KnifeItem;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public class KnifeModelLoadingPlugin implements ModelLoadingPlugin {

    public static final ModelResourceLocation KNIFE_MODEL_ID = ModelResourceLocation.inventory(KnifeItem.ITEM_ID);

    public static ResourceLocation getModelLocation(KnifeItem.Skin skin, Variant variant) {
        var skinPart = skin == KnifeItem.Skin.DEFAULT ? "" : "_%s".formatted(skin.getName());
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return KNIFE_MODEL_ID.id().withPath(path -> "item/%s%s%s".formatted(path, skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // make sure all models get loaded
        for (KnifeItem.Skin skin : KnifeItem.Skin.values()) {
            for (Variant variant : Variant.values()) {
                pluginContext.addModels(getModelLocation(skin, variant));
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            // replace the original knife model with our custom one
            if(KNIFE_MODEL_ID.equals(context.topLevelId())) {
                return new KnifeModel(unbakedModel);
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
