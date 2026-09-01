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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.util.TaskInstinctManager;
import io.wifi.starrailexpress.client.SecurityCameraClientState;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.utils.client.betterrender.TextBatchingBuffer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
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

import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.content.block.scene.DebrisPileBlock;
import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;

import java.awt.*;
import java.util.ArrayList;
import java.util.OptionalDouble;

// 如果你想添加新的类型，本类请不要修改，请使用 interface TaskInstinctShowableInterface
public class TaskBlockOverlayRenderer {
    // 创建带厚度的永远不被遮挡线框
    public static ArrayList<BlockPos> RoomDoorPositions = new ArrayList<>();
    /** 透视指示器的最大渲染距离（方块），更远的方块直接跳过。 */
    private static final int MAX_OVERLAY_DISTANCE_SQ = 128 * 128;
    private static final int MAX_OVERLAY_DISTANCE_ULTRA_SQ = 64 * 64;
    /**
     * 独立的线条缓冲：任务点/箭头/指引线都写进这里，并在 AFTER_TRANSLUCENT
     * 回调末尾统一冲刷。避免线条滞留/混入 GUI 共享缓冲 —— 共享缓冲的冲刷时机
     * 取决于下一个不同 render type 何时请求缓冲，导致透视时好时坏。
     */
    public static final TextBatchingBuffer OVERLAY_LINES = new TextBatchingBuffer();
    public static final RenderType ALWAYS_VISIBLE_THICK_LINES = RenderType.create("always_visible_thick_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0))) // 线宽4.0
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** 在 AFTER_TRANSLUCENT 回调末尾调用：确定性地绘制所有透视线条。 */
    public static void flushLines() {
        // 1. 所有任务点/指引方框累积在 lineBuilder 中，一次 drawWithShader 绘制。
        if (lineBuilder != null) {
            MeshData mesh = lineBuilder.buildOrThrow();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            RenderSystem.lineWidth(4.0f);
            BufferUploader.drawWithShader(mesh);
            RenderSystem.lineWidth(1.0f);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            mesh.close();
            LINE_BYTES.clear();
            lineBuilder = null;
        }
        // 2. 箭头/指引射线（独立缓冲），显式关闭深度测试。
        RenderSystem.disableDepthTest();
        OVERLAY_LINES.flush();
        RenderSystem.enableDepthTest();
    }

    /** 帧级线条累积缓冲：所有任务点框写入这里，flushLines() 时一次性绘制。 */
    private static final ByteBufferBuilder LINE_BYTES = new ByteBufferBuilder(65536);
    private static BufferBuilder lineBuilder;

    public static void renderBlockOverlay(WorldRenderContext context,
            BlockPos blockPos, Color color, float alpha, boolean colorize, float textScale) {
        Minecraft client = Minecraft.getInstance();
        Level world = client.level;
        if (world == null)
            return;

        // 距离裁剪：太远的方块跳过，避免逐帧做 AABB 与顶点计算
        Vec3 cameraPos = context.camera().getPosition();
        double dx = blockPos.getX() + 0.5 - cameraPos.x;
        double dy = blockPos.getY() + 0.5 - cameraPos.y;
        double dz = blockPos.getZ() + 0.5 - cameraPos.z;
        int maxRenderDistance = MAX_OVERLAY_DISTANCE_SQ;
        if (SREClientConfig.instance().isUltraPerfMode()) {
            maxRenderDistance = MAX_OVERLAY_DISTANCE_ULTRA_SQ;
        }
        if (dx * dx + dy * dy + dz * dz > maxRenderDistance) {
            return;
        }

        BlockState state = world.getBlockState(blockPos);
        AABB localAABB = getCombinedAABB(world, blockPos, state);

        PoseStack matrices = context.matrixStack();
        matrices.pushPose();

        matrices.translate(
                blockPos.getX() - cameraPos.x,
                blockPos.getY() - cameraPos.y,
                blockPos.getZ() - cameraPos.z);

        float red = color.getRed() / 255f;
        float green = color.getGreen() / 255f;
        float blue = color.getBlue() / 255f;

        // ✅ 累积到帧级线条缓冲，flushLines() 时一次性绘制（避免每方块一次
        // drawWithShader 导致大量 draw call）。绘制时显式禁用深度测试/深度写入/
        // 面剔除，必定完整穿透方块显示。
        if (lineBuilder == null) {
            lineBuilder = new BufferBuilder(LINE_BYTES, VertexFormat.Mode.LINES,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
        }
        LevelRenderer.renderLineBox(matrices, lineBuilder, localAABB, red, green, blue, alpha);

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
        if (DeathPenaltyComponent.hasStrictPenalty(instance.player)) {
            return;
        }
        // 监控模式下，非杀手不能看到任务点透视
        if (SecurityCameraClientState.isInSecurityMode() && !SREClient.isKiller())
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
            if (TaskInstinctManager.isTaskInstinctTypeShowable(-1) && item.is(TMMItems.KEY)) {
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
        {
            shouldDisplay[11] = true;
        }
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

        // 用户自定义选项
        for (int i = 0; i < shouldDisplay.length; i++) {
            shouldDisplay[i] = shouldDisplay[i] && TaskInstinctManager.isTaskInstinctTypeShowable(i);
        }

        // 渲染
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
                    if (shouldDisplay[type])
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 174, 201), 1f,
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
                case 24:
                    if (isBellMeetingEnabled(renderContext)) {
                        TaskBlockOverlayRenderer.renderBlockOverlay(renderContext, pos,
                                new Color(255, 215, 0), 1f, true, 0f);
                    }
                    break;
                default:
                    if (TaskInstinctManager.isTaskInstinctTypeShowable(type)) {
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

    /** 检查当前地图是否启用了摇铃会议 */
    private static boolean isBellMeetingEnabled(WorldRenderContext context) {
        var areas = AreasWorldComponent.KEY.get(context.world());
        if (areas == null)
            return false;
        return areas.areasSettings.bellMeetingEnabled && areas.areasSettings.meetingEnabled;
    }

}
