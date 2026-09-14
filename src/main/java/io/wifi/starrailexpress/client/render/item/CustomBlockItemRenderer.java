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
import io.wifi.starrailexpress.client.render.block.CustomBlockAppearance;
import io.wifi.starrailexpress.customblock.CustomBlockData;
import io.wifi.starrailexpress.customblock.CustomBlockLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自定义方块物品的渲染器（手上 / 背包 / 掉落物）。
 *
 * <p>
 * 与放置后的方块用同一套外观来源（{@link CustomBlockAppearance}），所以背包图标与实际放置
 * 后的样子一致。物品模型为 {@code builtin/entity}（见 {@code models/item/custom_block.json}），
 * 各场景的朝向 / 缩放由该模型的 {@code display} 变换负责，这里只把模型画在 {@code [0,1]³}。
 *
 * <p>
 * <b>性能</b>：物品一次只渲染一个，继承方块直接用
 * {@code BlockRenderDispatcher.renderSingleBlock} 画（只发顶点、不做世界 AO 计算），
 * 不需要为「放置的方块」准备的那套顶点捕获缓存。
 */
@Environment(EnvType.CLIENT)
public class CustomBlockItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    /** 配置变化 / 资源重载后清空缓存（这里只有贴图解析缓存，与方块渲染器共用）。 */
    public static void clearCache() {
        CustomBlockAppearance.clearCache();
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay) {
        CustomBlockData data = CustomBlockLoader.getData(stack);
        if (data == null) {
            CustomBlockAppearance.renderPlaceholder(poseStack, buffers, light, overlay);
            return;
        }
        // ① 资源包贴图
        ResourceLocation packTexture = CustomBlockAppearance.resolvePackTexture(data.packTexturePath);
        if (packTexture != null) {
            CustomBlockAppearance.renderTexturedCube(poseStack, buffers, packTexture, light, overlay);
            return;
        }
        // ② 继承方块模型
        BlockState inherited = CustomBlockLoader.inheritedState(data, Direction.NORTH, false);
        if (inherited == null) {
            CustomBlockAppearance.renderPlaceholder(poseStack, buffers, light, overlay);
            return;
        }
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(inherited, poseStack, buffers, light, overlay);
    }
}
