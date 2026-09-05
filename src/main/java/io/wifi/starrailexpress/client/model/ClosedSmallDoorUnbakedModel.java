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
 * along with this program.  If you did not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 关闭小门的 chunk 网格。贴图通过 {@link ClosedSmallDoorModels} 额外的
 * {@link BlockModel} 打进方块图集；这里只按 facing/half 取四套已经烘焙好的面。
 */
public final class ClosedSmallDoorUnbakedModel implements UnbakedModel {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final List<BakedQuad> EMPTY = List.of();

    private final ResourceLocation atlasTexture;

    public ClosedSmallDoorUnbakedModel(ResourceLocation tesrTexture) {
        this.atlasTexture = atlasId(tesrTexture);
    }

    public static ResourceLocation atlasId(ResourceLocation tesrTexture) {
        String path = tesrTexture.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        return ResourceLocation.fromNamespaceAndPath(tesrTexture.getNamespace(), path);
    }

    /** 给 ModelBakery 收集贴图用的真正 BlockModel（必须是 BlockModel 且贴图被 element 引用才会进图集）。 */
    public static UnbakedModel stitchModel(ResourceLocation tesrTexture) {
        return stitchModels(List.of(tesrTexture));
    }

    public static UnbakedModel stitchModels(Iterable<ResourceLocation> tesrTextures) {
        StringBuilder textures = new StringBuilder();
        StringBuilder elements = new StringBuilder();
        int i = 0;
        for (ResourceLocation tesrTexture : tesrTextures) {
            String tex = atlasId(tesrTexture).toString();
            String key = "t" + i;
            if (i > 0) {
                textures.append(',');
                elements.append(',');
            }
            textures.append('"').append(key).append("\":\"").append(tex).append('"');
            if (i == 0) {
                textures.append(",\"particle\":\"").append(tex).append('"');
            }
            int x = i % 16;
            int y = i / 16;
            elements.append("{\"from\":[").append(x).append(',').append(y).append(",0],\"to\":[")
                    .append(x + 1).append(',').append(y + 1).append(",1],\"faces\":{\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#")
                    .append(key).append("\"}}}");
            i++;
        }
        if (i == 0) {
            return BlockModel.fromString("{\"textures\":{\"particle\":\"minecraft:missingno\"},\"elements\":[]}");
        }
        return BlockModel.fromString("{\"textures\":{" + textures + "},\"elements\":[" + elements + "]}");
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelLoader) {
    }

    public static BakedModel bake(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return null;
        }
        return new Baked(sprite);
    }

    @Override
    public @Nullable BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState state) {
        return bake(spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, this.atlasTexture)));
    }

    private static final class Variant {
        private final List<BakedQuad> all;
        private final Map<Direction, List<BakedQuad>> byFace = new EnumMap<>(Direction.class);

        private Variant(List<BakedQuad> all) {
            this.all = all;
            for (Direction dir : Direction.values()) {
                this.byFace.put(dir, new ArrayList<>());
            }
            for (BakedQuad quad : all) {
                this.byFace.get(quad.getDirection()).add(quad);
            }
        }
    }

    private static final class Baked implements BakedModel {
        private final TextureAtlasSprite particle;
        private final Variant lowerZ;
        private final Variant upperZ;
        private final Variant lowerX;
        private final Variant upperX;

        private Baked(TextureAtlasSprite sprite) {
            this.particle = sprite;
            this.lowerZ = new Variant(bakeSlab(sprite, false, false));
            this.upperZ = new Variant(bakeSlab(sprite, true, false));
            this.lowerX = new Variant(bakeSlab(sprite, false, true));
            this.upperX = new Variant(bakeSlab(sprite, true, true));
        }

        private Variant pick(@Nullable BlockState state) {
            boolean upper = state != null
                    && state.hasProperty(SmallDoorBlock.HALF)
                    && state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER;
            boolean xAxis = state != null
                    && state.hasProperty(DoorPartBlock.FACING)
                    && state.getValue(DoorPartBlock.FACING).getAxis() == Direction.Axis.X;
            if (upper) {
                return xAxis ? this.upperX : this.upperZ;
            }
            return xAxis ? this.lowerX : this.lowerZ;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, RandomSource random) {
            if (state != null && state.hasProperty(BlockStateProperties.OPEN)
                    && state.getValue(BlockStateProperties.OPEN)) {
                return EMPTY;
            }
            Variant variant = this.pick(state);
            if (face == null) {
                return variant.all;
            }
            List<BakedQuad> quads = variant.byFace.get(face);
            return quads == null ? EMPTY : quads;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this.particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    private static List<BakedQuad> bakeSlab(TextureAtlasSprite sprite, boolean upper, boolean xAxis) {
        float v0 = upper ? 0.5f : 4.5f;
        float v1 = upper ? 4.5f : 8.5f;
        Vector3f from;
        Vector3f to;
        if (xAxis) {
            from = new Vector3f(7, 0, 0);
            to = new Vector3f(9, 16, 16);
        } else {
            from = new Vector3f(0, 0, 7);
            to = new Vector3f(16, 16, 9);
        }
        List<BakedQuad> quads = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            float[] uv = uvFor(dir, xAxis, v0, v1);
            BlockFaceUV faceUv = new BlockFaceUV(uv, 0);
            BlockElementFace face = new BlockElementFace(dir, -1, "#door", faceUv);
            quads.add(FACE_BAKERY.bakeQuad(from, to, face, sprite, dir, BlockModelRotation.X0_Y0, null, true));
        }
        return List.copyOf(quads);
    }

    private static float[] uvFor(Direction dir, boolean xAxis, float v0, float v1) {
        if (xAxis) {
            return switch (dir) {
                case EAST -> new float[] { 7.5f, v0, 11.5f, v1 };
                case WEST -> new float[] { 12f, v0, 16f, v1 };
                case NORTH -> new float[] { 7f, v0, 7.5f, v1 };
                case SOUTH -> new float[] { 11.5f, v0, 12f, v1 };
                case UP -> new float[] { 7.5f, 0f, 11.5f, 0.5f };
                case DOWN -> new float[] { 12f, 0f, 16f, 0.5f };
            };
        }
        return switch (dir) {
            case NORTH -> new float[] { 7.5f, v0, 11.5f, v1 };
            case SOUTH -> new float[] { 12f, v0, 16f, v1 };
            case WEST -> new float[] { 7f, v0, 7.5f, v1 };
            case EAST -> new float[] { 11.5f, v0, 12f, v1 };
            case UP -> new float[] { 7.5f, 0f, 11.5f, 0.5f };
            case DOWN -> new float[] { 12f, 0f, 16f, 0.5f };
        };
    }
}
