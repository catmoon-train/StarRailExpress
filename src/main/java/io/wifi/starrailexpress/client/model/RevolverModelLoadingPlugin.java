package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.item.RevolverItem;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public class RevolverModelLoadingPlugin implements ModelLoadingPlugin {

    public static final ModelResourceLocation REVOLVER_MODEL_ID = ModelResourceLocation.inventory(ResourceLocation.tryParse("starrailexpress:revolver"));

    public static ResourceLocation getModelLocation(SkinManager.RevolverSkin skin, Variant variant) {
        var skinPart = skin == SkinManager.RevolverSkin.REVOLVER_DEFAULT_SKIN ? "" : "_%s".formatted(skin.getName());
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return REVOLVER_MODEL_ID.id().withPath(path -> "item/%s%s%s".formatted(REVOLVER_MODEL_ID.id().getPath(), skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        for (SkinManager.Skin skin : SkinManager.getRevolverSkins().values()) {
            for (Variant variant : Variant.values()) {
                pluginContext.addModels(getModelLocation((SkinManager.RevolverSkin) skin, variant));
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            if(REVOLVER_MODEL_ID.equals(context.topLevelId())) {
                return new RevolverModel(unbakedModel);
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
