package org.agmas.noellesroles.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.ChefPlateBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 厨师「客户端」食物盘 / 饮料盘的盘内物品渲染器。
 *
 * <p>参数与本模组原有的 {@code io.wifi.starrailexpress.client.render.block_entity.PlateBlockEntityRenderer}
 * <b>逐项一致</b>：物品高度（食物盘 0.0375 / 饮料盘 0.225）、食物 75° 平躺、缩放 0.4、
 * {@link ItemDisplayContext#FIXED}、16 格距离剔除。坐标、光照、遮挡全部由引擎的方块实体
 * 渲染管线提供，因此和原版食物盘里的东西看起来一模一样，也不会有漂移或不渲染的问题。
 *
 * <p>唯一区别：原版把物品沿半径 0.25 的圆周排开并各自朝向外圈，这里改成<b>居中</b>摆放。
 */
public class ChefPlateRenderer implements BlockEntityRenderer<ChefPlateBlockEntity> {

    private static final double MAX_RENDER_DISTANCE_SQ = 16.0 * 16.0;
    private static final double CENTER_X = 0.5;
    private static final double CENTER_Z = 0.5;
    private static final float ITEM_SCALE = 0.4f;
    private static final float FOOD_TILT_DEGREES = 75.0f;

    private final ItemRenderer itemRenderer;

    public ChefPlateRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(@NotNull ChefPlateBlockEntity entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || entity.getLevel() == null) {
            return;
        }

        List<ItemStack> items = entity.getStoredItems();
        if (items == null || items.isEmpty()) {
            return;
        }

        // 距离剔除：与原版盘子渲染器一致
        Vec3 center = Vec3.atCenterOf(entity.getBlockPos());
        if (player.distanceToSqr(center) > MAX_RENDER_DISTANCE_SQ) {
            return;
        }

        renderItems(entity, items, matrices, vertexConsumers, light, overlay);
    }

    private void renderItems(@NotNull ChefPlateBlockEntity entity, List<ItemStack> items,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        boolean isDrink = entity.isDrink();
        // 与原版一致：食物贴着盘面，饮料杯摆在稍高的地方
        double centerY = (isDrink ? 0.225 : 0.0375);

        int maxRender = SREConfig.isUltraPerfMode() ? 6 : 12;
        int itemCount = Math.min(items.size(), maxRender);

        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            matrices.pushPose();
            // 居中摆放（局部坐标，矩阵已由引擎平移到方块位置）
            matrices.translate(CENTER_X, centerY, CENTER_Z);
            if (!isDrink) {
                // 食物平躺在盘里（和原版一样）；饮料保持直立。均为固定朝向，不做任何自转。
                matrices.mulPose(Axis.XP.rotationDegrees(FOOD_TILT_DEGREES));
            }
            matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                    matrices, vertexConsumers, entity.getLevel(), 0);
            matrices.popPose();
        }
    }
}
