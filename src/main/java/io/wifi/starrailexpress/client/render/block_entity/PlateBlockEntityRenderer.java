package io.wifi.starrailexpress.client.render.block_entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlateBlockEntityRenderer implements BlockEntityRenderer<BeveragePlateBlockEntity> {
    private final ItemRenderer itemRenderer;
    
    // 渲染距离限制（方块数的平方）
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0 * 64.0; // 64个方块的距离

    public PlateBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(@NotNull BeveragePlateBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        // 检查渲染距离
        if (!shouldRender(entity)) {
            return;
        }
        
        if (entity.isDrink()) {
            this.renderDrinks(entity, matrices, vertexConsumers, light, overlay);
        } else {
            this.renderFood(entity, matrices, vertexConsumers, light, overlay);
        }
    }
    
    /**
     * 检查是否应该渲染该方块实体
     * @param entity 方块实体
     * @return 是否应该渲染
     */
    private boolean shouldRender(@NotNull BeveragePlateBlockEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        
        // 如果没有玩家或世界为空，则不渲染
        if (player == null || entity.getLevel() == null) {
            return false;
        }
        
        // 计算玩家与方块实体之间的距离平方
        double distanceSq = player.distanceToSqr(
            entity.getBlockPos().getX() + 0.5,
            entity.getBlockPos().getY() + 0.5,
            entity.getBlockPos().getZ() + 0.5
        );
        
        // 如果距离超过最大渲染距离，则不渲染
        return distanceSq <= MAX_RENDER_DISTANCE_SQ;
    }

    public void renderFood(@NotNull BeveragePlateBlockEntity entity, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        int itemCount = entity.getStoredItems().size();
        if (itemCount == 0) return;

        double radius = 0.25;
        double centerX = 0.5;
        double centerY = 0.0375;
        double centerZ = 0.5;

        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = entity.getStoredItems().get(i);
            if (stack == null) continue;

            double angle = (2 * Math.PI / itemCount) * i;

            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);

            matrices.pushPose();

            matrices.translate(x, centerY, z);

            float rotationDegrees = (float) Math.toDegrees(angle) + 90f;

            matrices.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
            matrices.mulPose(Axis.XP.rotationDegrees(75f));
            matrices.scale(0.4f, 0.4f, 0.4f);

            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, matrices, vertexConsumers, entity.getLevel(), 0);
            matrices.popPose();
        }
    }

    public void renderDrinks(@NotNull BeveragePlateBlockEntity entity, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        int itemCount = entity.getStoredItems().size();
        if (itemCount == 0) return;

        double radius = 0.25;
        double centerX = 0.5;
        double centerY = 0.225;
        double centerZ = 0.5;

        for (int i = 0; i < itemCount; i++) {
            ItemStack stack = entity.getStoredItems().get(i);
            if (stack == null) continue;

            double angle = (2 * Math.PI / itemCount) * i;

            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);

            matrices.pushPose();

            matrices.translate(x, centerY, z);

            float rotationDegrees = (float) Math.toDegrees(angle) + 90f;

            matrices.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
            matrices.scale(0.4f, 0.4f, 0.4f);

            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, matrices, vertexConsumers, entity.getLevel(), 0);

            matrices.popPose();
        }
    }
}
