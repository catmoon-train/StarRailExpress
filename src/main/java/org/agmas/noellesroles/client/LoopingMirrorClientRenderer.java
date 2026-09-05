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

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.content.item.LoopingMirrorToolItem;
import io.wifi.starrailexpress.index.DevItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;
import org.agmas.noellesroles.scene.LoopingMirrorLoop;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 手持循环镜子工具时画出已绑定的两个平面，以及当前框选进度。
 */
public final class LoopingMirrorClientRenderer {
    private LoopingMirrorClientRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(LoopingMirrorClientRenderer::render);
        LoopingMirrorClientScenes.register();
    }

    private static void render(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = context.world();
        if (player == null || level == null || context.consumers() == null) {
            return;
        }
        boolean holdingTool = player.getMainHandItem().is(DevItems.LOOPING_MIRROR_TOOL)
                || player.getOffhandItem().is(DevItems.LOOPING_MIRROR_TOOL);
        if (!holdingTool && !player.isCreative()) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());

        if (holdingTool) {
            Set<LoopingMirrorLoop> loops = new HashSet<>();
            BlockPos origin = player.blockPosition();
            int originChunkX = origin.getX() >> 4;
            int originChunkZ = origin.getZ() >> 4;
            for (int cx = originChunkX - 6; cx <= originChunkX + 6; cx++) {
                for (int cz = originChunkZ - 6; cz <= originChunkZ + 6; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        if (entry.getValue() instanceof LoopingMirrorBlockEntity be && be.getLoop() != null) {
                            loops.add(be.getLoop());
                        }
                    }
                }
            }
            for (LoopingMirrorLoop loop : loops) {
                LevelRenderer.renderLineBox(poseStack, lines, loop.planeA().box(), 0.2F, 1.0F, 0.35F, 1.0F);
                LevelRenderer.renderLineBox(poseStack, lines, loop.planeB().box(), 1.0F, 0.25F, 0.85F, 1.0F);
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(loop.controller()), 0.35F, 0.85F, 1.0F, 1.0F);
            }
            renderSelection(poseStack, lines, player);
        }

        poseStack.popPose();
    }

    private static void renderSelection(PoseStack poseStack, VertexConsumer lines, LocalPlayer player) {
        ItemStack stack = player.getMainHandItem().is(DevItems.LOOPING_MIRROR_TOOL)
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!stack.is(DevItems.LOOPING_MIRROR_TOOL)) {
            return;
        }
        BlockPos host = LoopingMirrorToolItem.readHost(stack);
        if (host != null) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(host), 1.0F, 1.0F, 0.2F, 1.0F);
        }
        List<BlockPos> corners = LoopingMirrorToolItem.readCorners(stack);
        for (BlockPos corner : corners) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(corner), 1.0F, 0.7F, 0.2F, 0.9F);
        }
        if (corners.size() >= 2) {
            LevelRenderer.renderLineBox(poseStack, lines, cornerBox(corners.get(0), corners.get(1)), 0.2F, 1.0F, 0.5F, 0.7F);
        }
        if (corners.size() >= 4) {
            LevelRenderer.renderLineBox(poseStack, lines, cornerBox(corners.get(2), corners.get(3)), 1.0F, 0.4F, 0.8F, 0.7F);
        }
    }

    private static AABB cornerBox(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }
}
