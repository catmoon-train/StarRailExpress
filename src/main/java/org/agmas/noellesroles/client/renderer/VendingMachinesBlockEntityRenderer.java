package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.block.VendingMachinesBlock;
import org.agmas.noellesroles.content.block_entity.VendingMachinesBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VendingMachinesBlockEntityRenderer implements BlockEntityRenderer<VendingMachinesBlockEntity> {
    private final ItemRenderer itemRenderer;
    // 渲染距离限制（方块数的平方）
    private static final double MAX_RENDER_DISTANCE_SQ = 8.0 * 8.0; // 16个方块的距离

    public VendingMachinesBlockEntityRenderer(BlockEntityRendererProvider.@NotNull Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    /**
     * 检查是否应该渲染该方块实体
     * 
     * @param entity 方块实体
     * @return 是否应该渲染
     */
    private boolean shouldRender(@NotNull VendingMachinesBlockEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // 如果没有玩家或世界为空，则不渲染
        if (player == null || entity.getLevel() == null) {
            return false;
        }

        // 计算玩家与方块实体之间的距离平方
        double distanceSq = player.distanceToSqr(
                entity.getBlockPos().getX() + 0.5,
                entity.getBlockPos().getY() + 0.5,
                entity.getBlockPos().getZ() + 0.5);

        // 如果距离超过最大渲染距离，则不渲染
        return distanceSq <= MAX_RENDER_DISTANCE_SQ;
    }

    @Override
    public void render(@NotNull VendingMachinesBlockEntity entity, float tickDelta, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!shouldRender(entity)) {
            return;
        }
        this.renderGoods(entity, matrices, vertexConsumers, light, overlay);
    }

    public static float getBaseYawFromDirection(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            default -> 0.0F; // UP, DOWN 或者其他情况
        };
    }

    public void renderGoods(@NotNull VendingMachinesBlockEntity entity, PoseStack matrices,
            MultiBufferSource vertexConsumers, int light, int overlay) {
        List<ShopEntry> shops = entity.getShops();
        int itemCount = shops.size();
        if (itemCount == 0)
            return;
        final int ROW = 2;
        final int COL = 1;
        final int MMULT = ROW * COL;
        final int MAX_FLOOR = 4;
        // 2 * 2 * 4
        final Direction facing = entity.getBlockState().getValue(VendingMachinesBlock.FACING);
        float angle = getBaseYawFromDirection(facing);
        for (int i = 0; i < itemCount; i++) {
            var it = shops.get(i);
            ItemStack stack = it.stack();
            if (stack == null)
                continue;
            int floor = i / MMULT;// 哪一楼
            if (floor >= MAX_FLOOR)
                break;
            int k = i % MMULT;
            int r = k % ROW;
            // int c = k / ROW;
            float y = 0;
            if (floor == 0) {
                y = 1.7f;
            } else if (floor == 1) {
                y = 1.2f;
            } else if (floor == 2) {
                y = 0.7f;
            } else {
                y = 0.2f;
            }
            float x = 0.3f + 0.4f * r;
            float z = 0.5f;
            if (facing.equals(Direction.EAST) || facing.equals(Direction.WEST)) {
                z = x;
                x = 0.5f;
            }
            matrices.pushPose();

            matrices.translate(x, y, z);

            float rotationDegrees = 180 + (angle) * -1;
            // matrices.mulPose(Axis.XP.rotationDegrees(20));
            matrices.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
            matrices.scale(0.3f, 0.3f, 0.3f);

            this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, matrices, vertexConsumers,
                    entity.getLevel(), 0);
            matrices.popPose();
        }
    }
}
