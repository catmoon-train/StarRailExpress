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

package io.wifi.starrailexpress.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义方块外观的公共实现（方块渲染器与物品渲染器共用）。
 *
 * <p>
 * 三种外观来源，按优先级：
 * <ol>
 * <li>{@link #resolvePackTexture 资源包贴图}：在 [0,1]³ 画一个六面贴图方块；</li>
 * <li>{@link CustomBlockLoader#inheritedState 继承方块}：把该方块的烘焙模型顶点
 * {@link #captureModel 捕获一次}后逐帧重放（避免每帧完整 tesselate 的 AO / 光照计算）；</li>
 * <li>都没有：紫黑棋盘占位（提示"还没配外观"）。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public final class CustomBlockAppearance {

    /** 占位贴图（紫黑棋盘，随模组资源提供）。 */
    public static final ResourceLocation PLACEHOLDER_TEXTURE = SRE.id("textures/block/custom_block_placeholder.png");

    /** 资源包贴图解析缓存（key = 配置里填的原始字符串）。 */
    private static final Map<String, ResourceLocation> PACK_TEXTURE_CACHE = new ConcurrentHashMap<>();

    /** 共享随机源：捕获模型时避免每次分配（seed 每次重置，保证纹理一致）。 */
    private static final RandomSource RANDOM = RandomSource.create();

    private CustomBlockAppearance() {
    }

    /** 配置变化 / 资源重载后清空缓存。 */
    public static void clearCache() {
        PACK_TEXTURE_CACHE.clear();
    }

    // ==================== 贴图解析 ====================

    /**
     * 把配置里填的路径解析成贴图 {@link ResourceLocation}。
     *
     * <p>
     * 支持 {@code ns:textures/block/x.png}、{@code ns:block/x}、{@code textures/block/x.png}、
     * 以及不带目录的 {@code ns:x}（默认补 {@code block/}）。
     */
    public static ResourceLocation resolvePackTexture(String configured) {
        if (configured == null) {
            return null;
        }
        String raw = configured.trim();
        if (raw.isEmpty()) {
            return null;
        }
        ResourceLocation cached = PACK_TEXTURE_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        String namespace;
        String path;
        int split = raw.indexOf(':');
        if (split >= 0) {
            namespace = raw.substring(0, split).toLowerCase();
            path = raw.substring(split + 1);
        } else {
            namespace = "minecraft";
            path = raw;
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String assetsPrefix = "assets/" + namespace + "/";
        if (path.startsWith(assetsPrefix)) {
            path = path.substring(assetsPrefix.length());
        }
        if (!path.startsWith("textures/")) {
            path = "textures/" + (path.contains("/") ? path : "block/" + path);
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        ResourceLocation location = ResourceLocation.tryBuild(namespace, path);
        if (location == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return null;
        }
        if (minecraft.getResourceManager().getResource(location).isEmpty()) {
            // 资源包里没有这张贴图 → 当作没配置（不缓存未命中，便于实时改资源包）
            return null;
        }
        PACK_TEXTURE_CACHE.put(raw, location);
        return location;
    }

    // ==================== 六面贴图方块 ====================

    /** 用一张贴图在 [0,1]³ 画一个整方块（六个面各一个四边形，逆时针朝外）。 */
    public static void renderTexturedCube(PoseStack poseStack, MultiBufferSource buffers, ResourceLocation texture,
            int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        // 上（+Y）/ 下（-Y）
        face(consumer, matrix, pose, light, overlay, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0);
        face(consumer, matrix, pose, light, overlay, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, -1, 0);
        // 北（-Z）/ 南（+Z）
        face(consumer, matrix, pose, light, overlay, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, -1);
        face(consumer, matrix, pose, light, overlay, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1);
        // 西（-X）/ 东（+X）
        face(consumer, matrix, pose, light, overlay, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, -1, 0, 0);
        face(consumer, matrix, pose, light, overlay, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 0);
    }

    /**
     * 画一个四边形（两个三角形）。
     *
     * <p>
     * 四个顶点按「逆时针面向观察者」给出，UV 固定按
     * {@code (u0,v1) (u0,v0) (u1,v0) (u1,v1)} 分配（与原版 cube 各面的贴图方向一致）。
     */
    private static void face(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose, int light, int overlay,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float nx, float ny, float nz) {
        vertex(consumer, matrix, pose, x1, y1, z1, 0.0F, 1.0F, light, overlay, nx, ny, nz);
        vertex(consumer, matrix, pose, x2, y2, z2, 0.0F, 0.0F, light, overlay, nx, ny, nz);
        vertex(consumer, matrix, pose, x3, y3, z3, 1.0F, 0.0F, light, overlay, nx, ny, nz);
        vertex(consumer, matrix, pose, x4, y4, z4, 1.0F, 1.0F, light, overlay, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose pose,
            float x, float y, float z, float u, float v, int light, int overlay, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // ==================== 继承方块模型（捕获 + 重放）====================

    /**
     * 捕获一个方块状态的烘焙模型顶点（块局部坐标）。
     *
     * <p>
     * 每顶点 11 个 double：{@code [x, y, z, colorPacked, u, v, overlay, light, nx, ny, nz]}。
     * 捕获一次后逐帧只做「按相机平移重放」，比每帧 {@code renderSingleBlock} 的
     * 完整 tessellate（AO / 光照计算）便宜得多。
     */
    public static List<double[]> captureModel(BlockRenderDispatcher dispatcher, BlockAndTintGetter level,
            BlockState state, BlockPos pos) {
        BakedModel model = dispatcher.getBlockModel(state);
        long seed = state.getSeed(pos);
        CaptureConsumer capture = new CaptureConsumer();
        RANDOM.setSeed(seed);
        dispatcher.getModelRenderer().tesselateBlock(level, model, state, pos, new PoseStack(), capture, false,
                RANDOM, seed, OverlayTexture.NO_OVERLAY);
        return capture.vertices;
    }

    /** 重放捕获的顶点（按模型自身的渲染类型，透明 / 裁剪都正确；光照用捕获时的值）。 */
    public static void replay(PoseStack poseStack, MultiBufferSource buffers, List<double[]> vertices,
            BlockState state) {
        if (vertices == null || vertices.isEmpty()) {
            return;
        }
        RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(state);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        Matrix4f pose = poseStack.last().pose();
        for (double[] v : vertices) {
            consumer.addVertex(pose, (float) v[0], (float) v[1], (float) v[2])
                    .setColor((int) v[3])
                    .setUv((float) v[4], (float) v[5])
                    .setOverlay((int) v[6])
                    .setLight((int) v[7])
                    .setNormal((float) v[8], (float) v[9], (float) v[10]);
        }
    }

    // ==================== 占位 ====================

    /** 未配置外观时的紫黑棋盘占位方块。 */
    public static void renderPlaceholder(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        renderTexturedCube(poseStack, buffers, PLACEHOLDER_TEXTURE, light, overlay);
    }

    /** 该数据是否完全没有配置外观。 */
    public static boolean hasNoAppearance(CustomBlockData data) {
        if (data == null) {
            return true;
        }
        boolean hasInherit = data.inheritBlock != null && !data.inheritBlock.isBlank();
        boolean hasPack = resolvePackTexture(data.packTexturePath) != null;
        return !hasInherit && !hasPack;
    }

    /** 捕获一个方块的模型顶点：把 {@code tesselateBlock} 写出的顶点原样收集下来。 */
    private static final class CaptureConsumer implements VertexConsumer {
        private final List<double[]> vertices = new ArrayList<>();
        private double[] current;

        private void start(float x, float y, float z) {
            current = new double[11];
            current[0] = x;
            current[1] = y;
            current[2] = z;
            vertices.add(current);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            start(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current[3] = (r << 16) | (g << 8) | b | (a << 24);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current[4] = u;
            current[5] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current[6] = (u & 0xFFFF) | (v << 16);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current[7] = (u & 0xFFFF) | (v << 16);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int overlay) {
            current[6] = overlay;
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            current[7] = light;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            current[8] = nx;
            current[9] = ny;
            current[10] = nz;
            return this;
        }
    }
}
