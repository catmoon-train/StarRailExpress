package io.wifi.starrailexpress.client.model;



import io.wifi.starrailexpress.index.TMMCosmetics;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.item.KnifeItem;
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

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class KnifeModel implements UnbakedModel, BakedModel {

    /**
     * indexed by skin, then variant!
     */
    private final BakedModel[][] bakedModels = new BakedModel[KnifeItem.Skin.values().length][KnifeModelLoadingPlugin.Variant.values().length];
    private final UnbakedModel defaultUnbakedModel;

    public KnifeModel(UnbakedModel defaultUnbakedModel) {
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
    public @Nullable BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> textureGetter, ModelState settings) {
        for (KnifeItem.Skin skin : KnifeItem.Skin.values()) {
            for (KnifeModelLoadingPlugin.Variant variant : KnifeModelLoadingPlugin.Variant.values()) {
                bakedModels[skin.ordinal()][variant.ordinal()] = baker.bake(KnifeModelLoadingPlugin.getModelLocation(skin, variant), settings);
            }
        }

        return this;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    private static final Set<ItemDisplayContext> IN_HAND = EnumSet.of(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.FIXED);

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        var mode = context.itemTransformationMode();
        var variant = mode.firstPerson() || IN_HAND.contains(mode) ? KnifeModelLoadingPlugin.Variant.IN_HAND : KnifeModelLoadingPlugin.Variant.DEFAULT;
        
        // 从玩家的CCA组件获取皮肤，而不是仅依赖TMMCosmetics
        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null) {
            skinName = getSkinFromPlayerComponent(stack);
        }
        var skin = KnifeItem.Skin.fromString(skinName);

        bakedModels[skin.ordinal()][variant.ordinal()].emitItemQuads(stack, randomSupplier, context);
    }

    /**
     * 从玩家的CCA组件获取皮肤名称
     */
    private String getSkinFromPlayerComponent(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return SkinManager.getEquippedSkin(player, stack);
        }
        // 如果无法获取玩家或组件，则回退到原始方法
        return TMMCosmetics.getSkin(stack);
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
        return bakedModels[KnifeItem.Skin.DEFAULT.ordinal()][KnifeModelLoadingPlugin.Variant.DEFAULT.ordinal()];
    }
}