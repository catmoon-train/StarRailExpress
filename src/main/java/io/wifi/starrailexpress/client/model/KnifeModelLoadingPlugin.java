package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public class KnifeModelLoadingPlugin implements ModelLoadingPlugin {

    public static final ModelResourceLocation KNIFE_MODEL_ID = ModelResourceLocation.inventory(SRE.TMMId("knife"));
    public static final ModelResourceLocation NEW_KNIFE_MODEL_ID = ModelResourceLocation.inventory(SRE.id("knife"));

    public static ResourceLocation getModelLocation(SkinManager.Skin skin, Variant variant) {
        if (skin == SkinManager.KnifeSkin.DEFAULT_SKIN) {
            return KNIFE_MODEL_ID.id().withPath(path -> "item/%s".formatted(KNIFE_MODEL_ID.id().getPath()));
        }
        var skinPart = "%s".formatted(skin.getName());
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return NEW_KNIFE_MODEL_ID.id().withPath(
                path -> "item/skins/%s/%s%s".formatted(NEW_KNIFE_MODEL_ID.id().getPath(), skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        // make sure all models get loaded
        for (SkinManager.Skin skin : SkinManager.getSkins("knife").values()) {
            for (Variant variant : Variant.values()) {
                pluginContext.addModels(getModelLocation(skin, variant));
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            // replace the original knife model with our custom one
            var mid = context.topLevelId();
            
            if (KNIFE_MODEL_ID.equals(mid)) {
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
