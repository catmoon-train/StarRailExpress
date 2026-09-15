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

package io.wifi.starrailexpress.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义列车物品渲染器。
 *
 * <p>
 * 所有自定义列车物品共用 {@code custom_item} 这一个物品，物品模型为 {@code builtin/entity}，
 * 外观按 {@link CustomItemData.TextureMode} 三选一（编辑界面里用一个按钮切换，三者相互独立）：
 * <ol>
 * <li><b>PACK 资源包贴图</b>：{@link CustomItemData#packTexturePath}，{@code ns:item/x} 与
 * {@code ns:textures/item/x.png} 两种写法都支持，直接从资源包取贴图渲染（不需要进图集），
 * 画成前后两层 = 1 像素厚。</li>
 * <li><b>ANIMATED 导入动态贴图</b>：{@link CustomItemData#animatedTextures}（帧数不限）按
 * {@link CustomItemData#animatedFrameTicks} 循环，每帧都是上面那种平面贴图。</li>
 * <li><b>MODEL 导入立体贴图</b>：{@link CustomItemData#inheritItemTexture} 填物品 id，
 * 直接渲染该物品的完整模型（{@code elements} 立体、图集动画贴图都跟着走）。</li>
 * </ol>
 *
 * <p>
 * 当前来源没配 / 解析不到时逐级兜底：MODEL/ANIMATED/PACK → 被引用物品的主贴图（{@code getParticleIcon}）
 * → {@link #FALLBACK_TEXTURE_ITEM}（石头），而不是什么都不画 —— 否则未配置材质的物品在背包 / 手上
 * 看起来像空气，容易让人以为物品没了。
 *
 * <p>
 * 物品编辑界面里的预览仍然用紫黑棋盘表示「还没配外观」，那是有意的提示，与这里的兜底无关。
 */
@Environment(EnvType.CLIENT)
public class CustomItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /**
     * 渲染「模型地址」指定的 {@link BakedModel} 时用的占位物品栈。
     *
     * <p>
     * {@code ItemRenderer.render(..., model)} 要求物品栈非空（内部先判 {@code isEmpty()}），
     * 而模型是我们自己传进去的，栈本身只用于附魔光效等判断，所以用一个共用的石头栈即可。
     */
    private static final ItemStack DUMMY_STACK = new ItemStack(Items.STONE);

    /** 解析成功的资源包贴图缓存（key = 配置里填的原始字符串）。 */
    private static final Map<String, ResourceLocation> PACK_TEXTURE_CACHE = new ConcurrentHashMap<>();
    /** 解析成功的继承贴图缓存（key = 物品 id）。 */
    private static final Map<String, TextureAtlasSprite> INHERITED_SPRITE_CACHE = new ConcurrentHashMap<>();

    /** 配置变化后清空缓存（客户端同步 / 资源重载时调用）。 */
    public static void clearCache() {
        PACK_TEXTURE_CACHE.clear();
        INHERITED_SPRITE_CACHE.clear();
    }

    /** 兜底外观：没有配置资源包贴图、也没写材质继承时显示这个物品的贴图（石头）。 */
    public static final String FALLBACK_TEXTURE_ITEM = "minecraft:stone";

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null) {
            // 连数据都没有的裸物品（直接拿到的注册物品）也画成石头，避免看起来像空气
            drawFallback(poseStack, buffers, light, overlay);
            return;
        }

        // 材质来源三选一（编辑界面里用按钮切换，三者相互独立、只生效选中的那个）：
        // PACK / ANIMATED 是「贴图来源」，都还能再填一个模型地址当外壳；MODEL 是「模型来源」
        switch (data.textureMode()) {
            case PACK -> {
                if (drawTextured(data, resolvePackTexture(data.packTexturePath), poseStack, buffers, light,
                        overlay)) {
                    return;
                }
            }
            case ANIMATED -> {
                if (drawTextured(data, currentAnimatedFrame(data), poseStack, buffers, light, overlay)) {
                    return;
                }
            }
            case MODEL -> {
                if (drawModel(data, poseStack, buffers, light, overlay)) {
                    return;
                }
            }
        }

        // 当前来源没配 / 解析不到 → 退到「被引用物品的主贴图」，再退到石头（不再是什么都不画）
        TextureAtlasSprite sprite = resolveInheritedSprite(data.inheritItemTexture);
        if (sprite != null) {
            drawQuad(poseStack, buffers, RenderType.entityTranslucent(sprite.atlasLocation()),
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), light, overlay);
            return;
        }
        drawFallback(poseStack, buffers, light, overlay);
    }

    /**
     * PACK / ANIMATED 两种「贴图来源」的渲染：贴图取到之后再看有没有填「模型地址」——
     * 填了就用该模型当外壳、把这张贴图套上去；没填（或模型没烘焙上）走<b>默认模型</b>：
     * 一张平面四边形画成前后两层（1 像素厚）。
     *
     * @param texture 本次要用的贴图（资源包贴图 / 动态贴图当前帧；解析不到传 null）
     * @return 是否已经画出来
     */
    private static boolean drawTextured(CustomItemData data, ResourceLocation texture, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay) {
        if (texture == null) {
            return false;
        }
        BakedModel shell = resolveConfiguredModel(data.modelPath);
        if (shell != null) {
            drawModelWithTexture(shell, texture, poseStack, buffers, light, overlay);
            return true;
        }
        drawQuad(poseStack, buffers, RenderType.entityTranslucent(texture),
                0.0F, 0.0F, 1.0F, 1.0F, light, overlay);
        return true;
    }

    /** 动态贴图当前帧的贴图（没配帧 / 帧解析不到返回 null）。 */
    private static ResourceLocation currentAnimatedFrame(CustomItemData data) {
        List<String> frames = data.animatedFramePaths();
        if (frames.isEmpty()) {
            return null;
        }
        int frameTicks = Math.max(1, data.animatedFrameTicks);
        int index = (int) (gameTime() / frameTicks % frames.size());
        return resolvePackTexture(frames.get(index));
    }

    /**
     * 用指定贴图渲染一个烘焙模型：几何与 UV 都跟着模型走，只把"采样哪张图"换成我们这张，
     * 等价于给模型换皮；动态贴图逐帧换皮就是模型上的动画。
     *
     * <p>
     * 模型烘焙后的 UV 是<b>图集坐标</b>，而我们的贴图是独立纹理（0..1 = 整张图），
     * 所以要按该 quad 原本使用的 sprite 把 UV 换算回 sprite 内部坐标，否则会采到图集别处。
     */
    private static void drawModelWithTexture(BakedModel model, ResourceLocation texture, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            emitQuads(consumer, pose, model.getQuads(null, direction, random), light, overlay);
        }
        emitQuads(consumer, pose, model.getQuads(null, null, random), light, overlay);
    }

    /** 把一批 quad 的顶点写进给定 consumer（实际采样哪张贴图由 consumer 的渲染类型决定）。 */
    private static void emitQuads(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads, int light,
            int overlay) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            float u0 = sprite == null ? 0.0F : sprite.getU0();
            float v0 = sprite == null ? 0.0F : sprite.getV0();
            float du = sprite == null ? 1.0F : sprite.getU1() - sprite.getU0();
            float dv = sprite == null ? 1.0F : sprite.getV1() - sprite.getV0();
            if (du == 0.0F) {
                du = 1.0F;
            }
            if (dv == 0.0F) {
                dv = 1.0F;
            }
            Direction direction = quad.getDirection();
            int[] data = quad.getVertices();
            int stride = Math.max(1, data.length / 4);
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                if (base + 5 >= data.length) {
                    break;
                }
                float x = Float.intBitsToFloat(data[base]);
                float y = Float.intBitsToFloat(data[base + 1]);
                float z = Float.intBitsToFloat(data[base + 2]);
                float u = (Float.intBitsToFloat(data[base + 4]) - u0) / du;
                float v = (Float.intBitsToFloat(data[base + 5]) - v0) / dv;
                consumer.addVertex(pose, x, y, z)
                        .setColor(255, 255, 255, 255)
                        .setUv(u, v)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ());
            }
        }
    }

    /**
     * MODEL 导入立体模型：<b>模型地址</b>（资源包模型 json；没填时退回「借某个物品的模型」）
     * ＋ <b>材质地址</b>（可选：填了就给模型换皮，留空用模型自带贴图）。
     *
     * <p>
     * 组合规则：
     * <ul>
     * <li>有模型 + 有贴图 → 模型的几何 + 你导入的贴图（{@link #drawModelWithTexture}）；</li>
     * <li>有模型 + 没贴图 → 模型自带贴图（走原版物品渲染器）；</li>
     * <li>没模型 + 有贴图 → 默认模型（平面四边形两层）+ 这张贴图；</li>
     * <li>都没有 → 返回 false，交给外层兜底链。</li>
     * </ul>
     *
     * <p>
     * 渲染模型一律用 {@link ItemDisplayContext#NONE}：物品模型一般不给 {@code none} 配 display
     * 变换，解析出来是单位变换 —— 于是模型用的就是「本物品自己那套 display」（外层
     * {@code ItemRenderer} 已经施加过），不会被叠加第二次变换；坐标空间也与我们画平面时用的
     * [0,1]³ 一致（原版模型空间 0..16 = 1 格）。
     *
     * <p>
     * 借来的物品若本身是自定义物品，直接跳过，否则会递归渲染自己。
     */
    private static boolean drawModel(CustomItemData data, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getItemRenderer() == null) {
            return false;
        }
        // 材质地址：模型模式里它是「给模型换的皮」，留空就用模型自带的贴图
        ResourceLocation texture = resolvePackTexture(data.packTexturePath);
        BakedModel configured = resolveConfiguredModel(data.modelPath);
        if (configured != null) {
            if (texture != null) {
                drawModelWithTexture(configured, texture, poseStack, buffers, light, overlay);
            } else {
                minecraft.getItemRenderer().render(DUMMY_STACK, ItemDisplayContext.NONE, false, poseStack, buffers,
                        light, overlay, configured);
            }
            return true;
        }
        ItemStack inherited = resolveInheritedStack(data.inheritItemTexture);
        if (!inherited.isEmpty() && CustomItemLoader.getData(inherited) == null) {
            minecraft.getItemRenderer().renderStatic(inherited, ItemDisplayContext.NONE, light, overlay, poseStack,
                    buffers, minecraft.level, 0);
            return true;
        }
        // 模型地址空着但给了贴图：退回默认模型（平面两层）
        if (texture != null) {
            drawQuad(poseStack, buffers, RenderType.entityTranslucent(texture),
                    0.0F, 0.0F, 1.0F, 1.0F, light, overlay);
            return true;
        }
        return false;
    }

    /**
     * 「模型地址」→ 烘焙模型。
     *
     * <p>
     * 模型来自资源包 {@code assets/<ns>/models/<path>.json}，由
     * {@link io.wifi.starrailexpress.client.model.CustomItemModelPlugin} 登记成 Fabric extra model
     * 后在资源加载阶段烘焙；这里按 {@code standalone} 取（取不到再试 {@code inventory}）。
     *
     * @return 解析不出来 / 还没烘焙（例如刚改了地址还没重载资源）返回 null，交给调用方兜底
     */
    private static BakedModel resolveConfiguredModel(String modelPath) {
        ResourceLocation id = CustomItemData.resolveModelId(modelPath);
        if (id == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getModelManager() == null) {
            return null;
        }
        BakedModel missing = minecraft.getModelManager().getMissingModel();
        // Fabric extra model 烘焙出来挂在 "fabric_resource" 变体下
        // （见 ModelLoadingConstants.RESOURCE_SPECIAL_VARIANT）；顺带再试一次物品模型的 inventory 变体
        for (String variant : new String[] { "fabric_resource", "inventory" }) {
            BakedModel model = minecraft.getModelManager().getModel(new ModelResourceLocation(id, variant));
            if (model != null && model != missing) {
                return model;
            }
        }
        return null;
    }

    /** 解析被引用的物品栈（物品不存在 / 写法非法返回空栈）。 */
    private static ItemStack resolveInheritedStack(String configuredItemId) {
        if (configuredItemId == null) {
            return ItemStack.EMPTY;
        }
        String raw = configuredItemId.trim();
        if (raw.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.tryParse(raw);
        if (location == null) {
            location = ResourceLocation.tryBuild("minecraft", raw);
        }
        if (location == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /** 客户端世界时间（GUI 里没有世界时用现实时间兜底，保证动态贴图照样动）。 */
    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            return minecraft.level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }

    /** 兜底外观（石头贴图）；连石头都解析不到时保持不渲染。 */
    private static void drawFallback(PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        TextureAtlasSprite sprite = resolveInheritedSprite(FALLBACK_TEXTURE_ITEM);
        if (sprite == null) {
            return;
        }
        drawQuad(poseStack, buffers, RenderType.entityTranslucent(sprite.atlasLocation()),
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), light, overlay);
    }

    /**
     * 把配置里填的路径解析成贴图 {@link ResourceLocation}。
     *
     * <p>
     * 支持：{@code ns:textures/item/x.png}、{@code ns:item/x}、{@code textures/item/x.png}（默认 minecraft）。
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
        // 去掉可能写上的 assets/<ns>/ 前缀
        String assetsPrefix = "assets/" + namespace + "/";
        if (path.startsWith(assetsPrefix)) {
            path = path.substring(assetsPrefix.length());
        }
        if (!path.startsWith("textures/")) {
            path = "textures/" + (path.startsWith("item/") || path.contains("/") ? path : "item/" + path);
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

    /** 取被继承物品的主贴图。 */
    public static TextureAtlasSprite resolveInheritedSprite(String configuredItemId) {
        if (configuredItemId == null) {
            return null;
        }
        String raw = configuredItemId.trim();
        if (raw.isEmpty()) {
            return null;
        }
        TextureAtlasSprite cached = INHERITED_SPRITE_CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        ResourceLocation itemLocation = ResourceLocation.tryParse(raw);
        if (itemLocation == null) {
            itemLocation = ResourceLocation.tryBuild("minecraft", raw);
        }
        if (itemLocation == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemLocation);
        if (item == null || item == Items.AIR) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getItemRenderer() == null) {
            return null;
        }
        try {
            BakedModel model = minecraft.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
            if (model == null) {
                return null;
            }
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (sprite == null) {
                return null;
            }
            INHERITED_SPRITE_CACHE.put(raw, sprite);
            return sprite;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单层相对中心的偏移：0.5/16。
     *
     * <p>
     * 这里的四边形占满 [0,1]²（= 原版模型空间的 0..16），所以 1 个贴图像素 = 1/16；
     * 前后两层各偏 0.5/16，合起来正好 <b>1 个像素厚</b> —— 与原版 {@code item/generated}
     * 的两层（z=7.5 / 8.5）完全一致。
     */
    private static final float LAYER_OFFSET = 0.5F / 16.0F;

    /**
     * 在 [0,1]² 平面上画一个 item/generated 风格的物品（<b>前后两层</b>）。
     *
     * <p>
     * 只画 z=0.5 的单个平面时，物品是一张没有厚度的纸片（比 1 像素还薄），而且从背面看是空的。
     * 这里按原版 {@code item/generated} 的做法画两层：正面朝 +Z、背面朝 -Z（顶点顺序相反、
     * UV 跟着同一个角走，所以从背后看不会左右镜像）。
     */
    private static void drawQuad(PoseStack poseStack, MultiBufferSource buffers, RenderType renderType,
            float u0, float v0, float u1, float v1, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        float front = 0.5F - LAYER_OFFSET;
        float back = 0.5F + LAYER_OFFSET;

        // 正面（朝 +Z）
        consumer.addVertex(matrix, 0.0F, 1.0F, front).setColor(255, 255, 255, 255).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 1.0F, 1.0F, front).setColor(255, 255, 255, 255).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 1.0F, 0.0F, front).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 0.0F, 0.0F, front).setColor(255, 255, 255, 255).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);

        // 背面（朝 -Z，顶点顺序反过来）
        consumer.addVertex(matrix, 0.0F, 0.0F, back).setColor(255, 255, 255, 255).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(matrix, 1.0F, 0.0F, back).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(matrix, 1.0F, 1.0F, back).setColor(255, 255, 255, 255).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
        consumer.addVertex(matrix, 0.0F, 1.0F, back).setColor(255, 255, 255, 255).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, -1.0F);
    }
}
