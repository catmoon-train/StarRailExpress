package org.agmas.noellesroles.client;

import java.awt.Color;
import java.util.ArrayList;
import java.util.OptionalDouble;

import org.agmas.noellesroles.init.ModItems;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.cca.StarPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TaskBlockOverlayRenderer {
    // 创建带厚度的永远不被遮挡线框
    public static ArrayList<BlockPos> RoomDoorPositions = new ArrayList<>();
    public static final RenderType ALWAYS_VISIBLE_THICK_LINES = RenderType.create("always_visible_thick_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0))) // 线宽4.0
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    public static void renderBlockOverlay(WorldRenderContext context,
            BlockPos blockPos, Color color, float alpha, boolean colorize, float textScale, Component text) {
        Minecraft client = Minecraft.getInstance();
        Level world = client.level;
        if (world == null)
            return;

        BlockState state = world.getBlockState(blockPos);
        AABB localAABB = getCombinedAABB(world, blockPos, state);
        MultiBufferSource vertexConsumers = context.consumers();
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(ALWAYS_VISIBLE_THICK_LINES);

        PoseStack matrices = context.matrixStack();
        matrices.pushPose();

        Vec3 cameraPos = context.camera().getPosition();
        matrices.translate(
                blockPos.getX() - cameraPos.x,
                blockPos.getY() - cameraPos.y,
                blockPos.getZ() - cameraPos.z);

        float red = color.getRed() / 255f;
        float green = color.getGreen() / 255f;
        float blue = color.getBlue() / 255f;

        // ✅ 方块描边：用 context.consumers()，配合 ITEM_ENTITY_TARGET+NO_DEPTH_TEST 实现透视
        // RenderSystem.lineWidth(4);
        LevelRenderer.renderLineBox(matrices, vertexConsumer, localAABB, red, green, blue, alpha);

        if (text != null) {
            if (textScale <= 0) {
                double blockWidthX = localAABB.maxX - localAABB.minX;
                double blockWidthZ = localAABB.maxZ - localAABB.minZ;
                double blockWidth = Math.max(blockWidthX, blockWidthZ);
                if (blockWidth <= 0 || blockWidth > 1)
                    blockWidth = 1.0;
                int textPixelWidth = client.font.width(text);
                if (textPixelWidth > 0)
                    textScale = (float) blockWidth * 0.75f / textPixelWidth;
            }

            double centerX = (localAABB.minX + localAABB.maxX) / 2.0;
            double centerY = (localAABB.minY + localAABB.maxY) / 2.0;
            double centerZ = (localAABB.minZ + localAABB.maxZ) / 2.0;
            if (cameraPos.distanceTo(blockPos.getCenter()) <= 3)
                renderTextAtAABBCenter(context, blockPos, centerX, centerY, centerZ, text,
                        textScale, color.getRGB(),
                        true);
        }

        matrices.popPose();
    }

    // ✅ 新增：计算多格方块的合并 AABB（坐标相对于 blockPos）
    private static AABB getCombinedAABB(Level world, BlockPos blockPos, BlockState state) {
        // 门（DoubleBlockHalf）：上下两格
        // 普通单格方块：用碰撞箱，fallback 用视觉箱
        VoxelShape shape = state.getCollisionShape(world, blockPos);
        if (shape.isEmpty())
            shape = state.getShape(world, blockPos);
        if (shape.isEmpty())
            return new AABB(0, 0, 0, 0, 0, 0);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.LOWER) {
                var b = state.getCollisionShape(world, blockPos.above());
                if (b.isEmpty())
                    return shape.bounds().expandTowards(0, 1,
                            0);
                var a = b.bounds();
                a = a.move(0, 1, 0);
                var c = shape.bounds();
                return new AABB(Math.min(a.minX, c.minX), Math.min(a.minY, c.minY), Math.min(a.minZ, c.minZ),
                        Math.max(a.maxX, c.maxX), Math.max(a.maxY, c.maxY), Math.max(a.maxZ, c.maxZ));
            } else {
                var b = state.getCollisionShape(world, blockPos.above());
                if (b.isEmpty())
                    return shape.bounds().expandTowards(0, 1,
                            0);
                var a = b.bounds().move(0, -1, 0);
                var c = shape.bounds();
                return new AABB(Math.min(a.minX, c.minX), Math.min(a.minY, c.minY), Math.min(a.minZ, c.minZ),
                        Math.max(a.maxX, c.maxX), Math.max(a.maxY, c.maxY), Math.max(a.maxZ, c.maxZ));
            }
        }

        // 床（BedPart）：沿朝向延伸一格
        if (state.hasProperty(BlockStateProperties.BED_PART) &&
                state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (part == BedPart.FOOT) {
                // 脚部：朝 facing 方向扩展一格
                return shape.bounds().expandTowards(facing.getStepX(), 0,
                        facing.getStepZ());
            } else {
                // 头部：朝反方向扩展一格
                Direction opp = facing.getOpposite();
                return shape.bounds()
                        .expandTowards(opp.getStepX(), 0, opp.getStepZ());
            }
        }

        return shape.bounds();
    }

    public static void renderTextAtAABBCenter(WorldRenderContext context,
            BlockPos blockPos,
            double localCX, double localCY, double localCZ,
            Component text, float scale, int color, boolean shadow) {

        Minecraft client = Minecraft.getInstance();
        PoseStack matrices = context.matrixStack();

        matrices.pushPose();
        matrices.translate(localCX, localCY, localCZ);

        Vec3 cameraPos = context.camera().getPosition();
        double dx = cameraPos.x - (blockPos.getX() + localCX);
        double dz = cameraPos.z - (blockPos.getZ() + localCZ);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        matrices.scale(scale, -scale, scale);
        Font font = client.font;
        matrices.translate(0, -((float) font.lineHeight) / 2f, 0);

        // ✅ 使用独立 BufferSource，不污染 context.consumers() 的线框缓冲
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        font.drawInBatch(
                text,
                -font.width(text) / 2.0f, 0,
                color, shadow,
                matrices.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0, 15728880);
        // ✅ 立即 flush，确保文字渲染状态不外泄
        bufferSource.endBatch();

        matrices.popPose();
    }

    public static void render(WorldRenderContext renderContext) {
        if (!NoellesrolesClient.isTaskInstinctEnabled)
            return;
        var instance = Minecraft.getInstance();
        if (instance == null)
            return;
        if (instance.player == null)
            return;
        if (instance.level == null)
            return;

        if (SREClient.gameComponent == null)
            return;
        if (!SREClient.gameComponent.isRunning())
            return;

        boolean shouldDisplay[] = new boolean[20];
        for (int i = 0; i < shouldDisplay.length; i++) {
            shouldDisplay[i] = false;
        }

        if (SREClient.isPlayerSpectatingOrCreative()) {
            for (int i = 0; i < shouldDisplay.length; i++) {
                shouldDisplay[i] = true;
            }
        }
        if (SREClient.isPlayerAliveAndInSurvival()) {
            var player = Minecraft.getInstance().player;
            var world = Minecraft.getInstance().level;
            var item = player.getMainHandItem();
            if (item.is(TMMItems.KEY)) {
                ItemLore lore = item.get(DataComponents.LORE);
                if (lore != null && !lore.lines().isEmpty()) {
                    NoellesrolesClient.myRoomNumber = lore.lines().getFirst().getString();
                    for (var ele : TaskBlockOverlayRenderer.RoomDoorPositions) {
                        if (world.getBlockEntity(ele) instanceof SmallDoorBlockEntity entity) {
                            if (entity.getKeyName().equals(NoellesrolesClient.myRoomNumber)) {
                                TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, ele,
                                        new Color(255, 247, 155),
                                        1f,
                                        true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.door"));
                            }
                        }

                    }
                }
            } else if (item.is(TMMItems.LETTER) || item.is(ModItems.LETTER_ITEM)) {
                shouldDisplay[11] = true;
            }

            // 拿着钥匙
            // RoomDoorPositions
        }
        /**
         * 1: 食物
         * 2: 水
         * 3: 洗澡
         * 4: 床
         * 5: 跑步机
         * 6: 讲台
         * 7: 门
         * 8: 马桶
         * 9: 椅子（包括马桶）
         * 10: 音符盒
         * 11: 售货机
         */

        var playerMood = StarPlayerMoodComponent.KEY.get(Minecraft.getInstance().player);
        if (playerMood != null) {
            for (var task : playerMood.tasks.values()) {
                switch (task.getType()) {
                    case BATHE:
                        shouldDisplay[3] = true;
                        break;
                    case DRINK:
                        shouldDisplay[2] = true;
                        break;
                    case EAT:
                        shouldDisplay[1] = true;
                        break;
                    case EXERCISE:
                        shouldDisplay[5] = true;
                        break;
                    case MEDITATE:
                        // 无
                        break;
                    case OUTSIDE:
                        // 无
                        break;
                    case RAED_BOOK:
                        shouldDisplay[6] = true;
                        break;
                    case SLEEP:
                        shouldDisplay[4] = true;
                        break;
                    case TOILET:
                        shouldDisplay[8] = true;
                        break;
                    case CHAIR:
                        shouldDisplay[9] = true;
                        break;
                    case NOTE_BLOCK:
                        shouldDisplay[10] = true;
                        break;
                    default:
                        break;

                }
            }
        }
        for (var set : NoellesrolesClient.taskBlocks.entrySet()) {
            var pos = set.getKey();
            int type = set.getValue();
            switch (type) { // 1: 食物 2: 水 3: 洗澡 4: 床 5: 跑步机 6: 讲台
                case 1:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, Color.GREEN, 1f, true, 0f,
                                Component.translatable("hud.noellesroles.task_instinct.render.task.food"));
                    break;
                case 2:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(234, 88, 88), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.drink"));
                    break;
                case 3:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(141, 234, 189), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.bathe"));
                    break;
                case 4:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(0, 255, 220), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.bed"));
                    break;
                case 5:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(255, 242, 0), 1f,
                                true, 0f,
                                Component.translatable("hud.noellesroles.task_instinct.render.task.running_machine"));
                    break;
                case 6:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 127, 39), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.lecture"));
                    break;
                case 7:
                    break;
                case 8:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 174, 201), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.toilet"));
                    break;
                case 9:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(126, 255, 228), 1f,
                                true, 0f, Component.translatable("hud.noellesroles.task_instinct.render.task.seat"));
                    break;
                case 10:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(121, 148, 255), 1f,
                                true, 0f,
                                Component.translatable("hud.noellesroles.task_instinct.render.task.note_block"));
                    break;
                case 11:
                    if (shouldDisplay[type]) {
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 174, 201), 1f,
                                true, 0f,
                                Component.translatable("hud.noellesroles.task_instinct.render.vending_machine"));
                    }
                default:
                    break;
            }
        }
        // 恢复渲染状态
        // 统一提交线框和文字的批次
        // Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

}
