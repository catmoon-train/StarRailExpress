package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
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
import org.agmas.noellesroles.content.block.scene.DebrisPileBlock;
import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;

import java.awt.*;
import java.util.ArrayList;
import java.util.OptionalDouble;

// 本类AI禁止修改
// 本类AI禁止修改
// 本类AI禁止修改
// 本类AI禁止修改
// 本类AI禁止修改
// 本类请不要修改，请使用 interface TaskInstinctShowableInterface
// 本类请不要修改，请使用 interface TaskInstinctShowableInterface
// 本类请不要修改，请使用 interface TaskInstinctShowableInterface
// 本类请不要修改，请使用 interface TaskInstinctShowableInterface
// 本类请不要修改，请使用 interface TaskInstinctShowableInterface
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
            BlockPos blockPos, Color color, float alpha, boolean colorize, float textScale) {
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

        // 监控模式下，非杀手不能看到任务点透视
        if (SecurityMonitorBlock.isInSecurityMode() && !SREClient.isKiller())
            return;

        boolean shouldDisplay[] = new boolean[64];
        for (int i = 0; i < shouldDisplay.length; i++) {
            shouldDisplay[i] = false;
        }

        if (SREClient.isPlayerSpectatingOrCreative()) {
            for (int i = 0; i < shouldDisplay.length; i++) {
                shouldDisplay[i] = true;
            }
        }
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        var world = client.level;
        if (SREClient.isPlayerAliveAndInSurvival()) {
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
                                        true, 0f);
                            }
                        }

                    }
                }
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
         * 12: 物资箱
         */
        var playerMood = SREPlayerMoodComponent.KEY.get(client.player);
        if (playerMood != null) {
            for (var task : playerMood.getTasks().values()) {
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
                    case BREATHE:
                        // 呼吸任务无需特殊方块高亮
                        break;
                    case LIGHT_STOVE:
                        shouldDisplay[16] = true; // 炉灶 — 橙色
                        break;
                    case CLEAN_DUST:
                        shouldDisplay[17] = true; // 灰尘 — 淡灰色
                        break;
                    case TRANSPORT:
                        shouldDisplay[18] = true; // 运输点起点 — 亮绿色
                        shouldDisplay[19] = true; // 运输点终点 — 深红色
                        break;
                    case PRAY:
                        shouldDisplay[20] = true; // 雕像 — 淡黄色
                        break;
                    case PRUNE_BUSH:
                        shouldDisplay[21] = true; // 灌木 — 黄绿色
                        break;
                    case HARVEST_CROP:
                        shouldDisplay[22] = true; // 草垫 — 棕黄色
                        break;
                    default:
                        break;

                }
            }
        }
        for (var set : NoellesrolesClient.taskBlocks.entrySet()) {
            var pos = set.getKey();
            int type = set.getValue();
            BlockState block = renderContext.world().getBlockState(pos);
            if (isActiveSabotageRepairBlock(block)) {
                TaskInstinctShowableInterface it = (TaskInstinctShowableInterface) block.getBlock();
                java.awt.Color c = it.taskInstinctRenderColor(block, pos, player);
                float alpha = c.getAlpha() / 255f;
                TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                        c, alpha,
                        true, 0f);
                continue;
            }
            switch (type) { // 1: 食物 2: 水 3: 洗澡 4: 床 5: 跑步机 6: 讲台
                case 1:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, Color.GREEN, 1f, true, 0f);
                    break;
                case 2:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(234, 88, 88), 1f,
                                true, 0f);
                    break;
                case 3:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(141, 234, 189), 1f,
                                true, 0f);
                    break;
                case 4:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(0, 255, 220), 1f,
                                true, 0f);
                    break;
                case 5:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos, new Color(255, 242, 0), 1f,
                                true, 0f);
                    break;
                case 6:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 127, 39), 1f,
                                true, 0f);
                    break;
                case 7:
                    break;
                case 8:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 174, 201), 1f,
                                true, 0f);
                    break;
                case 9:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(126, 255, 228), 1f,
                                true, 0f);
                    break;
                case 10:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(121, 148, 255), 1f,
                                true, 0f);
                    break;
                case 11:
                    TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                            new Color(255, 174, 201), 1f,
                            true, 0f);
                    break;
                case 13:
                    TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                            new Color(147, 112, 219), 1f,
                            true, 0f);
                    break;
                case 16:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 165, 0), 1f, true, 0f);
                    break;
                case 17:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(192, 192, 192), 1f, true, 0f);
                    break;
                case 18:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(0, 150, 50), 1f, true, 0f);
                    break;
                case 19:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(180, 40, 40), 1f, true, 0f);
                    break;
                case 20:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 255, 180), 1f, true, 0f);
                    break;
                case 21:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(173, 255, 47), 1f, true, 0f);
                    break;
                case 22:
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(218, 165, 32), 1f, true, 0f);
                    break;
                default:
                    if (block.getBlock() instanceof TaskInstinctShowableInterface it) {
                        // 给我tmd老老实实的用api判断！！！！！！！！！！！！
                        if (it.shouldRenderTaskInstinct(renderContext.world(), block, pos, player)) {
                            java.awt.Color c = it.taskInstinctRenderColor(block, pos, player);
                            float alpha = c.getAlpha() / 255f;
                            TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                    c, alpha,
                                    true, 0f);
                        }
                    }
                    break;
            }
        }
        // 恢复渲染状态
        // 统一提交线框和文字的批次
        // Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    private static boolean isActiveSabotageRepairBlock(BlockState state) {
        if (state.getBlock() instanceof ReactorBlock) {
            return state.getValue(ReactorBlock.ACTIVE) && !state.getValue(ReactorBlock.CLOSED);
        }
        if (state.getBlock() instanceof WaterValveBlock) {
            return state.getValue(WaterValveBlock.ACTIVE) && !state.getValue(WaterValveBlock.CLOSED);
        }
        if (state.getBlock() instanceof DebrisPileBlock) {
            return state.getValue(DebrisPileBlock.ACTIVE) && !state.getValue(DebrisPileBlock.CLOSED);
        }
        return false;
    }

}
