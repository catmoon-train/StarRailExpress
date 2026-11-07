package io.wifi.starrailexpress.mixin.compat.sre;

import io.wifi.srepackcompat.startup.SreModelLookup;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 仅替换候选遍历；原回调的判断、GeneralModel 构造和返回保持不变。 */
@Mixin(targets = "io.wifi.starrailexpress.client.model.GeneralModelLoadingPlugin", remap = false)
public abstract class GeneralModelLookupMixin {
    @Shadow @Final private static Map<String, Set<ModelResourceLocation>> BASE_MODELS;
    @Shadow @Final private static Map<String, List<String>> STAGE_SUFFIXES;

    @Inject(method = "onInitializeModelLoader", at = @At("HEAD"))
    private void srepack$buildCandidates(ModelLoadingPlugin.Context context, CallbackInfo ci) {
        SreModelLookup.rebuild(BASE_MODELS, STAGE_SUFFIXES);
    }

    @Redirect(method = "lambda$onInitializeModelLoader$4", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private static Set<Map.Entry<String, Set<ModelResourceLocation>>> srepack$onLoadCandidates(
            Map<String, Set<ModelResourceLocation>> source, UnbakedModel model, ModelModifier.OnLoad.Context context) {
        return SreModelLookup.candidates(source, context.topLevelId().id(), false);
    }

    @Redirect(method = "lambda$onInitializeModelLoader$3", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private static Set<Map.Entry<String, Set<ModelResourceLocation>>> srepack$beforeBakeCandidates(
            Map<String, Set<ModelResourceLocation>> source, UnbakedModel model, ModelModifier.BeforeBake.Context context) {
        return SreModelLookup.candidates(source, context.resourceId(), true);
    }
}
