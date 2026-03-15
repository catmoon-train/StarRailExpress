package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public class GrenadeModelLoadingPlugin implements ModelLoadingPlugin {

    public static final ModelResourceLocation GRENADE_MODEL_ID = ModelResourceLocation
            .inventory(SRE.TMMId("grenade"));

    public static final ModelResourceLocation NEW_GRENADE_MODEL_ID = ModelResourceLocation
            .inventory(SRE.id("grenade"));

    public static ResourceLocation getModelLocation(SkinManager.Skin skin, Variant variant) {
        if (skin == SkinManager.GrenadeSkin.GRENADE_DEFAULT_SKIN) {
            return GRENADE_MODEL_ID.id().withPath(path -> "item/%s".formatted(GRENADE_MODEL_ID.id().getPath()));
        }
        var skinPart = "%s".formatted(skin.getName());
        var variantPart = variant == Variant.DEFAULT ? "" : "_%s".formatted(variant.getSerializedName());

        return NEW_GRENADE_MODEL_ID.id().withPath(
                path -> "item/skins/%s/%s%s".formatted(NEW_GRENADE_MODEL_ID.id().getPath(), skinPart, variantPart));
    }

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        for (SkinManager.Skin skin : SkinManager.getSkins("grenade").values()) {
            for (Variant variant : Variant.values()) {
                pluginContext.addModels(getModelLocation(skin, variant));
            }
        }

        pluginContext.modifyModelOnLoad().register((unbakedModel, context) -> {
            if (GRENADE_MODEL_ID.equals(context.topLevelId())) {
                return new GrenadeModel(unbakedModel);
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
