package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.index.SRECosmetics;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class BatModel implements UnbakedModel, BakedModel {

    private final Map<String, Map<BatModelLoadingPlugin.Variant, BakedModel>> bakeModels = new HashMap<>();
    private final UnbakedModel defaultUnbakedModel;

    public BatModel(UnbakedModel defaultUnbakedModel) {
        this.defaultUnbakedModel = defaultUnbakedModel;
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return defaultUnbakedModel.getDependencies();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelLoader) {
        defaultUnbakedModel.resolveParents(modelLoader);
    }

    @Override
    public @Nullable BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter,
            ModelState settings) {
        for (SkinManager.Skin skin : SkinManager.getSkins("bat").values()) {
            for (BatModelLoadingPlugin.Variant variant : BatModelLoadingPlugin.Variant.values()) {
                var bakedModel = baker.bake(BatModelLoadingPlugin.getModelLocation(skin, variant),
                        settings);
                if (bakeModels.containsKey(skin.getName()))
                    bakeModels.get(skin.getName()).put(variant, bakedModel);
                else {
                    bakeModels.put(skin.getName(), new HashMap<>());
                    bakeModels.get(skin.getName()).put(variant, bakedModel);
                }
            }
        }

        return this;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    private static final Set<ItemDisplayContext> IN_HAND = EnumSet.of(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.FIXED);

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        var mode = context.itemTransformationMode();
        var variant = mode.firstPerson() || IN_HAND.contains(mode) ? BatModelLoadingPlugin.Variant.IN_HAND
                : BatModelLoadingPlugin.Variant.DEFAULT;

        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null) {
            skinName = getSkinFromPlayerComponent(stack);
        }
        var skin = SkinManager.Skin.fromString("bat", skinName);

        if (bakeModels.containsKey(skin.getName()) && bakeModels.get(skin.getName()).containsKey(variant))
            bakeModels.get(skin.getName()).get(variant).emitItemQuads(stack, randomSupplier, context);
        else
            getDefaultModel().emitItemQuads(stack, randomSupplier, context);
    }

    private String getSkinFromPlayerComponent(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return SkinManager.getEquippedSkin(player, stack);
        }
        // 如果无法获取玩家或组件，则回退到原始方法
        return SRECosmetics.getSkin(stack);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
        return getDefaultModel().getQuads(state, face, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return getDefaultModel().useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return getDefaultModel().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return getDefaultModel().usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return getDefaultModel().isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return getDefaultModel().getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return getDefaultModel().getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return getDefaultModel().getOverrides();
    }

    private BakedModel getDefaultModel() {
        return bakeModels.get(SkinManager.BatSkin.BAT_DEFAULT_SKIN.getName())
                .get(BatModelLoadingPlugin.Variant.DEFAULT);
    }
}
