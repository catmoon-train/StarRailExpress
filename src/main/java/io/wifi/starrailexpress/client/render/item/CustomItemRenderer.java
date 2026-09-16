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
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
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
 * 外观按 {@link CustomItemData.TextureMode} 四选一（编辑界面里用一个按钮切换，四种相互独立）：
 * <ol>
 * <li><b>PACK 资源包贴图</b>：{@link CustomItemData#packTexturePath}，{@code ns:item/x} 与
 * {@code ns:textures/item/x.png} 两种写法都支持，直接从资源包取贴图渲染（不需要进图集），
 * 画成前后两层 = 1 像素厚。</li>
 * <li><b>ANIMATED 导入动态贴图</b>：{@link CustomItemData#animatedTextures}（帧数不限）按
 * {@link CustomItemData#animatedFrameTicks} 循环，每帧都是上面那种平面贴图。</li>
 * <li><b>MODEL 导入立体贴图</b>：{@link CustomItemData#inheritItemTexture} 填物品 id，
 * 直接渲染该物品的完整模型（{@code elements} 立体、图集动画贴图都跟着走）。</li>
 * <li><b>INHERIT 继承现有物品贴图</b>：{@link CustomItemData#inheritItemTexture} 填物品 id，
 * 整份借用该物品的模型与材质（{@link #drawInherited}）。</li>
 * </ol>
 *
 * <p>
 * 当前来源没配 / 解析不到时逐级兜底：MODEL/ANIMATED/PACK/INHERIT → 被继承物品的<b>完整模型</b>
 * （{@link #drawInherited}）→ {@link #FALLBACK_TEXTURE_ITEM}（石头），而不是什么都不画 —— 否则
 * 未配置材质的物品在背包 / 手上看起来像空气，容易让人以为物品没了。
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
        if (data.holdOrientation() == CustomItemData.HoldOrientation.HORIZONTAL && isHandContext(mode)) {
            // 「横着拿」：抵掉物品模型自带 display（原版 item/handheld 那套）的旋转与偏移，
            // 换成原版普通物品（item/generated，苹果那类）的姿态
            poseStack.pushPose();
            applyFlatItemHold(poseStack, mode);
            renderAppearance(data, poseStack, buffers, light, overlay);
            poseStack.popPose();
            return;
        }
        renderAppearance(data, poseStack, buffers, light, overlay);
    }

    /** 是否手持场景（第一 / 第三人称的手）：只有这里才需要处理「横着拿」。 */
    private static boolean isHandContext(ItemDisplayContext mode) {
        return mode.firstPerson()
                || mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    // ==================== 手持方向（横着拿）====================

    /*
     * 物品模型 custom_item.json 的 display 取自原版 item/handheld（工具 / 剑那套）；下面是
     * 「自带那套」与「原版 item/generated（苹果那类普通物品）」的数值，选「横着拿」时用它们把前者
     * 抵掉、换成后者。display 里的平移是按 0..16 写的（解析时乘以 1/16）。
     */
    private static final float HOLD_SELF_SCALE_THIRD = 0.55F;
    private static final float HOLD_SELF_SCALE_FIRST = 0.68F;
    private static final float HOLD_SELF_ROT_Y_THIRD = -90.0F;
    private static final float HOLD_SELF_ROT_Z_THIRD = 55.0F;
    private static final float HOLD_SELF_ROT_Y_FIRST = -90.0F;
    private static final float HOLD_SELF_ROT_Z_FIRST = 25.0F;
    private static final float HOLD_SELF_TX_FIRST = 1.13F / 16.0F;
    private static final float HOLD_SELF_TY_THIRD = 4.0F / 16.0F;
    private static final float HOLD_SELF_TZ_THIRD = 0.5F / 16.0F;
    private static final float HOLD_SELF_TY_FIRST = 3.2F / 16.0F;
    private static final float HOLD_SELF_TZ_FIRST = 1.13F / 16.0F;
    private static final float HOLD_VANILLA_TY_THIRD = 3.0F / 16.0F;
    private static final float HOLD_VANILLA_TZ_THIRD = 1.0F / 16.0F;
    private static final float HOLD_VANILLA_TX_FIRST = 1.13F / 16.0F;
    private static final float HOLD_VANILLA_TY_FIRST = 3.2F / 16.0F;
    private static final float HOLD_VANILLA_TZ_FIRST = 1.13F / 16.0F;

    /**
     * 「横着拿」的手持姿态修正。
     *
     * <p>
     * 外层已经施加了物品模型自带的 display（原版 {@code item/handheld}：绕 Y -90°、绕 Z 55°/25°，
     * 平移 [0,4,0.5] / [1.13,3.2,1.13]）。这里在<b>模型空间</b>里乘上它的逆旋转，再补上
     * 「自带平移 → 原版 {@code item/generated} 平移」的差值，于是最终姿态与苹果那类普通物品一致。
     * 两侧缩放相同（0.55 / 0.68），所以平移差值要再除一次缩放来抵消外层那一次缩放。
     */
    private static void applyFlatItemHold(PoseStack poseStack, ItemDisplayContext mode) {
        boolean leftHand = mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = mode.firstPerson();
        float scale = firstPerson ? HOLD_SELF_SCALE_FIRST : HOLD_SELF_SCALE_THIRD;
        float selfRotY = firstPerson ? HOLD_SELF_ROT_Y_FIRST : HOLD_SELF_ROT_Y_THIRD;
        float selfRotZ = firstPerson ? HOLD_SELF_ROT_Z_FIRST : HOLD_SELF_ROT_Z_THIRD;
        // 左手时原版会把 Y / Z 旋转与 X 平移取反（见 ItemTransform#apply）
        float appliedRotY = leftHand ? -selfRotY : selfRotY;
        float appliedRotZ = leftHand ? -selfRotZ : selfRotZ;
        float selfTx = firstPerson ? HOLD_SELF_TX_FIRST : 0.0F;
        float selfTy = firstPerson ? HOLD_SELF_TY_FIRST : HOLD_SELF_TY_THIRD;
        float selfTz = firstPerson ? HOLD_SELF_TZ_FIRST : HOLD_SELF_TZ_THIRD;
        float vanillaTx = firstPerson ? HOLD_VANILLA_TX_FIRST : 0.0F;
        float vanillaTy = firstPerson ? HOLD_VANILLA_TY_FIRST : HOLD_VANILLA_TY_THIRD;
        float vanillaTz = firstPerson ? HOLD_VANILLA_TZ_FIRST : HOLD_VANILLA_TZ_THIRD;

        // ① 逆旋转（rotationXYZ 三个角全取负即为逆旋转）
        poseStack.mulPose(new org.joml.Quaternionf().rotationXYZ(0.0F,
                -appliedRotY * net.minecraft.util.Mth.DEG_TO_RAD,
                -appliedRotZ * net.minecraft.util.Mth.DEG_TO_RAD));
        // ② 平移差值（自带平移在左手时 X 取反），并除掉外层那一次缩放
        float appliedSelfTx = leftHand ? -selfTx : selfTx;
        poseStack.translate((vanillaTx - appliedSelfTx) / scale, (vanillaTy - selfTy) / scale,
                (vanillaTz - selfTz) / scale);
    }

    /** 按配置画外观（材质来源四选一 + 兜底链）。 */
    private static void renderAppearance(CustomItemData data, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        // 材质来源四选一（编辑界面里用按钮切换，四者相互独立、只生效选中的那个）：
        // PACK / ANIMATED 是「贴图来源」，都还能再填一个模型地址当外壳；
        // MODEL 是「模型来源」，INHERIT 整份借用某个物品的模型与材质
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
            case INHERIT -> {
                if (drawInherited(data, poseStack, buffers, light, overlay)) {
                    return;
                }
            }
        }

        // 当前来源没配 / 解析不到时的兜底链（顺序即语义）：
        // ① 填了「继承现有物品」就整份用那个物品的模型与材质 —— 是整个模型，不是只贴它的一张平面主贴图
        //    （否则会出现「明明继承了模型，却只是贴了张平面图标」这种半吊子外观）
        // ② 再退到石头（不再是什么都不画，否则未配置材质的物品看起来像空气）
        if (drawInherited(data, poseStack, buffers, light, overlay)) {
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

    /**
     * 抵消嵌套渲染多出来的一次 {@code translate(-0.5, -0.5, -0.5)}。
     *
     * <p>
     * 烘焙后的模型顶点本来就在 <b>[0,1]³</b>（{@code FaceBakery} 会把 json 里的 0..16 除以 16），
     * 我们被调用时外层 {@code ItemRenderer.render} 已经做过 display 变换和
     * {@code translate(-0.5,-0.5,-0.5)} —— 也就是说我们已经站在「[0,1]³ 居中于物品格」的空间里，
     * {@link #drawQuad} 直接按 [0,1] 画正是这个原因。
     *
     * <p>
     * 而 {@code ItemRenderer#renderStatic} / {@code render} 自己还会再做一次同样的
     * {@code translate(-0.5)}，于是被嵌套渲染的模型会整体偏移半格（在物品栏里就是一半露在格子外）。
     * 这里补一个 {@code +0.5} 抵掉它，被继承 / 被引用的模型就与原版物品完全重合。
     */
    private static void undoSelfCentering(PoseStack poseStack) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
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
     * {@code ItemRenderer} 已经施加过），不会被叠加第二次变换；只需用
     * {@link #undoSelfCentering} 抵掉嵌套渲染多出来的那次 {@code translate(-0.5)}。
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
                poseStack.pushPose();
                undoSelfCentering(poseStack);
                minecraft.getItemRenderer().render(DUMMY_STACK, ItemDisplayContext.NONE, false, poseStack, buffers,
                        light, overlay, configured);
                poseStack.popPose();
            }
            return true;
        }
        if (drawInherited(data, poseStack, buffers, light, overlay)) {
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
     * INHERIT 继承现有物品：直接渲染 {@link CustomItemData#inheritItemTexture} 指定物品的
     * <b>模型与材质</b>（走原版物品渲染器，拿在手上 / 背包里和那个物品长得一模一样）。
     *
     * <p>
     * 借来的物品若本身是自定义列车物品，直接跳过，否则会递归渲染自己。
     *
     * <p>
     * 继承的是「模型与材质」：手持姿态（第一/第三人称的朝向与缩放）仍用本物品自己那套 display
     * （与标准物品一致），不跟随被继承物品的 display 表。
     *
     * @return 是否已经画出来
     */
    private static boolean drawInherited(CustomItemData data, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getItemRenderer() == null) {
            return false;
        }
        ItemStack inherited = resolveInheritedStack(data.inheritItemTexture);
        if (inherited.isEmpty() || CustomItemLoader.getData(inherited) != null) {
            return false;
        }
        poseStack.pushPose();
        undoSelfCentering(poseStack);
        minecraft.getItemRenderer().renderStatic(inherited, ItemDisplayContext.NONE, light, overlay, poseStack,
                buffers, minecraft.level, 0);
        poseStack.popPose();
        return true;
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

    // ==================== 食用 / 破碎粒子的贴图 ====================

    /**
     * 食用 / 破碎自定义列车物品时，粒子该用的 sprite。
     *
     * <p>
     * 原版 {@code ParticleTypes.ITEM} 粒子采样的是<b>方块图集</b>里「物品模型自带的 particle 贴图」，
     * 而 {@code custom_item} 的模型是 {@code builtin/entity}、没有贴图 ⇒ 原样是材质丢失的紫黑图标。
     * 配置里的贴图由 {@code CustomItemSpriteSource} 注册进方块图集，这里按材质来源取：
     * 资源包贴图 / 动态贴图第一帧 / 引用模型与被继承物品的 particle 图标，取不到时退回石头，
     * 保证不会是丢失材质。返回 {@code null} 表示不是自定义物品，保持原版行为。
     *
     * <p>
     * 图集在资源加载阶段拼接：改完贴图后要重载一次资源（F3+T）。
     * 由 {@code mixin.client.texture.BreakingItemParticleMixin} 调用。
     */
    public static TextureAtlasSprite resolveParticleSprite(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        CustomItemData data = CustomItemLoader.getData(stack);
        if (data == null || minecraft == null) {
            return null;
        }
        TextureAtlasSprite sprite = switch (data.textureMode()) {
            case PACK -> atlasSprite(data.packTexturePath);
            case ANIMATED -> {
                List<String> frames = data.animatedFramePaths();
                yield frames.isEmpty() ? null : atlasSprite(frames.get(0));
            }
            case MODEL -> configuredParticleIcon(data);
            case INHERIT -> inheritedParticleIcon(data.inheritItemTexture);
        };
        if (sprite == null) {
            // 来源没配 / 解析不到：退回继承物品，再退石头 —— 保证不会是材质丢失图标
            sprite = atlasSprite(data.packTexturePath);
        }
        if (sprite == null) {
            sprite = inheritedParticleIcon(data.inheritItemTexture);
        }
        if (sprite == null) {
            sprite = resolveInheritedSprite(FALLBACK_TEXTURE_ITEM);
        }
        return sprite;
    }

    /** 从方块图集取 sprite（图集在资源加载时拼接；缺失贴图视为没配，返回 null）。 */
    private static TextureAtlasSprite atlasSprite(String configured) {
        ResourceLocation file = resolvePackTexture(configured);
        if (file == null) {
            return null;
        }
        try {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(SpriteSource.TEXTURE_ID_CONVERTER.fileToId(file));
            return sprite == missingParticleIcon() ? null : sprite;
        } catch (Exception e) {
            return null;
        }
    }

    /** 「模型地址」指向的模型自带的 particle 图标（缺失贴图视为没配）。 */
    private static TextureAtlasSprite configuredParticleIcon(CustomItemData data) {
        BakedModel model = resolveConfiguredModel(data.modelPath);
        if (model == null) {
            return null;
        }
        TextureAtlasSprite sprite = model.getParticleIcon();
        return sprite == missingParticleIcon() ? null : sprite;
    }

    /** 被继承物品的 particle 图标（自定义列车物品无效；缺失贴图视为没配）。 */
    private static TextureAtlasSprite inheritedParticleIcon(String configuredItemId) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack inherited = resolveInheritedStack(configuredItemId);
        if (inherited.isEmpty() || CustomItemLoader.getData(inherited) != null
                || minecraft == null || minecraft.getItemRenderer() == null) {
            return null;
        }
        try {
            BakedModel model = minecraft.getItemRenderer().getModel(inherited, minecraft.level, null, 0);
            TextureAtlasSprite sprite = model == null ? null : model.getParticleIcon();
            return sprite == null || sprite == missingParticleIcon() ? null : sprite;
        } catch (Exception e) {
            return null;
        }
    }

    /** 「材质丢失」图标的 particle 图标（用来把缺失贴图识别成「没配」）。 */
    private static TextureAtlasSprite missingParticleIcon() {
        return Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon();
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
