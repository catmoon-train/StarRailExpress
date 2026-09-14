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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义列车物品渲染器。
 *
 * <p>
 * 所有自定义列车物品共用 {@code custom_item} 这一个物品，物品模型为 {@code builtin/entity}，
 * 具体贴图按物品数据决定：
 * <ol>
 * <li><b>资源包物品材质继承</b>：{@code ns:item/x} 与 {@code ns:textures/item/x.png} 两种写法都支持，
 * 直接从资源包取贴图渲染（不需要进图集）。</li>
 * <li><b>物品材质继承</b>：取被继承物品模型的主贴图（{@code getParticleIcon}）渲染。</li>
 * <li>两者都没有 → 兜底显示 {@link #FALLBACK_TEXTURE_ITEM}（石头）的贴图，而不是什么都不画
 * ——否则未配置材质的物品在背包 / 手上看起来像空气，容易让人以为物品没了。</li>
 * </ol>
 *
 * <p>
 * 物品编辑界面里的预览仍然用紫黑棋盘表示「还没配外观」，那是有意的提示，与这里的兜底无关。
 */
@Environment(EnvType.CLIENT)
public class CustomItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

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

        // ① 资源包贴图（与「物品材质继承」冲突时优先本项）
        ResourceLocation packTexture = resolvePackTexture(data.packTexturePath);
        if (packTexture != null) {
            drawQuad(poseStack, buffers, RenderType.entityTranslucent(packTexture),
                    0.0F, 0.0F, 1.0F, 1.0F, light, overlay);
            return;
        }

        // ② 继承某物品的贴图
        TextureAtlasSprite sprite = resolveInheritedSprite(data.inheritItemTexture);
        if (sprite != null) {
            drawQuad(poseStack, buffers, RenderType.entityTranslucent(sprite.atlasLocation()),
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), light, overlay);
            return;
        }

        // ③ 都没配置 → 显示石头（不再是什么都不画）
        drawFallback(poseStack, buffers, light, overlay);
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

    /** 在 [0,1]² 平面上画一个 item/generated 风格的四边形。 */
    private static void drawQuad(PoseStack poseStack, MultiBufferSource buffers, RenderType renderType,
            float u0, float v0, float u1, float v1, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(renderType);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        float z = 0.5F;
        consumer.addVertex(matrix, 0.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(u0, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 1.0F, 1.0F, z).setColor(255, 255, 255, 255).setUv(u1, v0)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 1.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(u1, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, 0.0F, 0.0F, z).setColor(255, 255, 255, 255).setUv(u0, v1)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
