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

import io.wifi.starrailexpress.index.SRECosmetics;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
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

public class GeneralModel implements UnbakedModel, BakedModel {

    /**
     * indexed by skin, then variant!
     */
    private final Map<String, Map<GeneralModelLoadingPlugin.Variant, BakedModel>> bakeModels = new HashMap<>();
    private final UnbakedModel defaultUnbakedModel;
    /** 物品自身的基准模型位置（不带 item/ 前缀，例如 {@code minecraft:bow}） */
    private final ModelResourceLocation defaultModelLocation;
    /** 分阶段物品的阶段后缀，如 {@code ""}、{@code "_pulling_0"}、{@code "_arrow"} */
    private final String stageSuffix;
    private BakedModel defaultBakedModel = null;
    private String itemType;

    public GeneralModel(String itemType, ModelResourceLocation defaulLocation, UnbakedModel defaultUnbakedModel) {
        this(itemType, defaulLocation, "", defaultUnbakedModel);
    }

    public GeneralModel(String itemType, ModelResourceLocation defaulLocation, String stageSuffix,
            UnbakedModel defaultUnbakedModel) {
        this.defaultUnbakedModel = defaultUnbakedModel;
        this.defaultModelLocation = defaulLocation;
        this.stageSuffix = stageSuffix == null ? "" : stageSuffix;
        this.itemType = itemType;
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
        var itml = defaultModelLocation.id();
        defaultBakedModel = baker.bake(itml.withPath("item/" + itml.getPath() + stageSuffix),
                settings);
        boolean staged = GeneralModelLoadingPlugin.isStaged(itemType);
        for (ItemSkinManager.Skin skin : ItemSkinManager.getSkins(itemType).values()) {
            for (GeneralModelLoadingPlugin.Variant variant : GeneralModelLoadingPlugin.Variant.values()) {
                // 分阶段物品只用阶段区分模型，不需要手持/GUI 变体
                if (staged && variant != GeneralModelLoadingPlugin.Variant.DEFAULT) {
                    continue;
                }
                ResourceLocation location = GeneralModelLoadingPlugin.getModelLocation(itemType, skin.getName(),
                        variant, stageSuffix);
                if (location == null) {
                    continue;
                }
                var bakedModel = baker.bake(location, settings);
                if ("default".equals(skin.getName()))
                    continue;
                // 皮肤已注册但模型文件缺失（例如弩未绘制「装填烟花」阶段贴图）时跳过，
                // 回退到该阶段的原版模型（GeneralModel 的默认模型即原版 <类型>_<阶段>，
                // 如 minecraft:item/crossbow_firework），避免露出紫黑缺失材质。
                if (bakedModel == null)
                    continue;
                // 分阶段皮肤（弓/弩）若某阶段模型文件缺失（例如弩未绘制「装填烟花」贴图），
                // 跳过该皮肤阶段模型，回退到原版对应阶段模型（GeneralModel 的默认模型即
                // 原版 <类型>_<阶段>，如 minecraft:item/crossbow_firework），避免紫黑缺失材质。
                var missing = Minecraft.getInstance().getModelManager().getMissingModel();
                if (GeneralModelLoadingPlugin.isStaged(itemType) && missing != null && bakedModel == missing)
                    continue;
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
        // 分阶段物品（弓/弩）的阶段已由原版 overrides 拆成独立模型，这里固定用 DEFAULT
        var variant = (!GeneralModelLoadingPlugin.isStaged(itemType)
                && (mode.firstPerson() || IN_HAND.contains(mode)))
                        ? GeneralModelLoadingPlugin.Variant.IN_HAND
                        : GeneralModelLoadingPlugin.Variant.DEFAULT;

        // 从玩家的CCA组件获取皮肤，而不是仅依赖TMMCosmetics
        String skinName = stack.get(SREDataComponentTypes.SKIN);
        if (skinName == null) {
            skinName = getSkinFromPlayerComponent(stack);
        }
        var skin = ItemSkinManager.Skin.fromString(itemType, skinName);

        Map<GeneralModelLoadingPlugin.Variant, BakedModel> variants = skin == null ? null
                : bakeModels.get(skin.getName());
        if (variants != null) {
            BakedModel baked = variants.get(variant);
            // 皮肤只提供了 GUI 模型时，手持也退化到同一份皮肤模型，避免露出原版贴图
            if (baked == null) {
                baked = variants.get(GeneralModelLoadingPlugin.Variant.DEFAULT);
            }
            if (baked != null) {
                baked.emitItemQuads(stack, randomSupplier, context);
                return;
            }
        }
        getDefaultModel().emitItemQuads(stack, randomSupplier, context);
    }

    /**
     * 从玩家的CCA组件获取皮肤名称
     */
    private String getSkinFromPlayerComponent(ItemStack stack) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return ItemSkinManager.getEquippedSkin(player, stack);
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
        if (!bakeModels.containsKey("default")) {
            return defaultBakedModel;
        }
        return bakeModels.get("default")
                .getOrDefault(GeneralModelLoadingPlugin.Variant.DEFAULT, defaultBakedModel);
    }
}
