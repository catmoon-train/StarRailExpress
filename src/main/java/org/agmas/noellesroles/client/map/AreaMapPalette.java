package org.agmas.noellesroles.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.agmas.noellesroles.mixin.client.map.AreaMapSpriteContentsAccessor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 从当前资源包的烘焙顶面取色；只缓存材质，位置相关的群系染色仍逐列计算。 */
final class AreaMapPalette {
    private record Layer(int color, int tint) { }
    private final Map<BlockState, List<Layer>> blocks = new IdentityHashMap<>();
    private final Map<SpriteContents, Integer> sprites = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create(0);

    void clear() {
        blocks.clear();
        sprites.clear();
    }

    int color(Minecraft mc, ClientLevel level, BlockPos pos, BlockState state) {
        var fluid = state.getFluidState();
        if (!fluid.isEmpty() && state.getRenderShape() != RenderShape.MODEL) {
            var handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());
            if (handler != null) {
                var textures = handler.getFluidSprites(level, pos, fluid);
                if (textures != null && textures.length > 0 && textures[0] != null) {
                    int color = AreaMapColor.tint(average(textures[0]), handler.getFluidColor(level, pos, fluid));
                    // 水保留底部材质；岩浆保持不透明，不让深水的每一层重复叠色。
                    return fluid.is(FluidTags.WATER) ? (color & 0xFFFFFF) | 0xB8000000 : color | 0xFF000000;
                }
            }
        }
        if (state.getRenderShape() == RenderShape.INVISIBLE) return 0;
        var layers = blocks.computeIfAbsent(state, key -> describe(mc, key));
        int color = 0;
        for (Layer layer : layers) {
            int pixel = layer.color();
            if (layer.tint() >= 0) pixel = AreaMapColor.tint(pixel, mc.getBlockColors().getColor(state, level, pos, layer.tint()));
            color = AreaMapColor.over(pixel, color);
        }
        if (!layers.isEmpty()) return color;
        MapColor fallback = state.getMapColor(level, pos);
        return fallback == MapColor.NONE ? 0 : fallback.calculateRGBColor(MapColor.Brightness.HIGH);
    }

    private List<Layer> describe(Minecraft mc, BlockState state) {
        var model = mc.getBlockRenderer().getBlockModel(state);
        List<Layer> result = new ArrayList<>();
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, Direction.UP, random)) add(result, quad);
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, null, random)) {
            if (quad.getDirection() == Direction.UP) add(result, quad);
        }
        if (result.isEmpty()) {
            var sprite = model.getParticleIcon();
            if (!sprite.contents().name().getPath().equals("missingno")) result.add(new Layer(average(sprite), 0));
        }
        return List.copyOf(result);
    }

    private void add(List<Layer> result, BakedQuad quad) {
        result.add(new Layer(average(quad.getSprite()), quad.isTinted() ? quad.getTintIndex() : -1));
    }

    private int average(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        return sprites.computeIfAbsent(contents, key -> {
            NativeImage image = ((AreaMapSpriteContentsAccessor) key).noellesroles$originalImage();
            int width = key.width(), height = key.height();
            int frame = key.getUniqueFrames().findFirst().orElse(0);
            int columns = Math.max(1, image.getWidth() / width);
            int ox = frame % columns * width, oy = frame / columns * height;
            AreaMapColor.Average average = new AreaMapColor.Average();
            int nx = Math.min(16, width), nz = Math.min(16, height);
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    average.add(image.getPixelRGBA(ox + x * width / nx, oy + z * height / nz));
                }
            }
            return average.color();
        });
    }
}
