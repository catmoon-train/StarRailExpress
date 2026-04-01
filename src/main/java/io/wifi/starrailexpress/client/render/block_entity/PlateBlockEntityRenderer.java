package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public class PlateBlockEntityRenderer implements BlockEntityRenderer<BeveragePlateBlockEntity> {
    private final ItemRenderer itemRenderer;
    private static final double MAX_RENDER_DISTANCE_SQ = 16.0 * 16.0;
    private static final double RADIUS = 0.25;
    private static final double CENTER_X = 0.5;
    private static final double CENTER_Z = 0.5;
    private static final float ITEM_SCALE = 0.4f;

    public PlateBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(@NotNull BeveragePlateBlockEntity entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || entity.getLevel() == null) return;

        List<ItemStack> items = entity.getStoredItems();
        if (items == null || items.isEmpty()) return;

        // 距离剔除
        Vec3 center = Vec3.atCenterOf(entity.getBlockPos());
        if (player.distanceToSqr(center) > MAX_RENDER_DISTANCE_SQ) return;

        renderItems(entity, items, matrices, vertexConsumers, light, overlay);
    }

    private void renderItems(@NotNull BeveragePlateBlockEntity entity, List<ItemStack> items,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        boolean isDrink = entity.isDrink();
        double centerY = isDrink ? 0.225 : 0.0375;

        int maxRender = SREConfig.isUltraPerfMode() ? 6 : 12;
        int itemCount = Math.min(items.size(), maxRender);
        double angleStep = 2 * Math.PI / itemCount;

        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.isEmpty()) continue;

            double angle = angleStep * i;
            double x = CENTER_X + RADIUS * Math.cos(angle);
            double z = CENTER_Z + RADIUS * Math.sin(angle);
            float rotDeg = (float) Math.toDegrees(angle) + 90f;

            matrices.pushPose();
            matrices.translate(x, centerY, z);
            matrices.mulPose(Axis.YP.rotationDegrees(rotDeg));
            if (!isDrink) {
                matrices.mulPose(Axis.XP.rotationDegrees(75f));
            }
            matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay,
                    matrices, vertexConsumers, entity.getLevel(), 0);
            matrices.popPose();
        }
    }
}