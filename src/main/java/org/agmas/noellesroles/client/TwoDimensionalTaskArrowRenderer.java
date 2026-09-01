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
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 二维（{@link ModEffects#TWO_DIMENSIONAL_CAMERA}）效果下的任务指引箭头：
 * 玩家刷新了 SAN 值局内任务后，在头顶绘制指向目标任务点的世界空间箭头（可同时存在多个）；
 * 手持钥匙时额外指向自己房门。目标点数据与颜色映射复用任务点透视
 * （{@link TaskBlockOverlayRenderer}，本类只读取其公开数据，不修改该类）。
 * <p>
 * 同种任务点聚集时合并为一个箭头（{@link #MERGE_DISTANCE} 格内视为同簇，紧贴的任务点
 * 不会拆分），距离超过阈值的同类点拆分为独立箭头。
 */
public final class TwoDimensionalTaskArrowRenderer {

    /** 同种任务点间距不超过该值时合并为一个箭头。 */
    private static final double MERGE_DISTANCE = 6.0;
    /** 最多同时渲染的箭头数，避免刷屏。 */
    private static final int MAX_ARROWS = 12;
    private static final double ARROW_LENGTH = 0.9;
    private static final double ARROW_HEAD = 0.28;
    private static final double ARROW_RISE = 0.55; // 高于头顶的距离

    private TwoDimensionalTaskArrowRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null)
            return;
        if (!player.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA))
            return;
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning())
            return;
        if (!SREClient.isPlayerAliveAndInSurvival())
            return;

        List<Target> targets = collectTargets(client, player);
        if (targets.isEmpty())
            return;

        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 origin = player.getPosition(partialTick).add(0, player.getBbHeight() + ARROW_RISE, 0);
        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer vertexConsumer = TaskBlockOverlayRenderer.OVERLAY_LINES
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack matrices = context.matrixStack();

        int rendered = 0;
        for (Target target : targets) {
            if (rendered++ >= MAX_ARROWS)
                break;
            drawArrow(matrices, vertexConsumer, origin, cameraPos, target);
        }
    }

    /** 收集所有箭头目标：当前 SAN 任务对应的任务点（按种类聚簇合并）+ 手持钥匙时的自己房门。 */
    private static List<Target> collectTargets(Minecraft client, LocalPlayer player) {
        List<Target> targets = new ArrayList<>();

        SREPlayerMoodComponent playerMood = SREPlayerMoodComponent.KEY.get(player);
        if (playerMood != null && !playerMood.getTasks().isEmpty()) {
            Set<Integer> wantedTypes = new HashSet<>();
            for (var task : playerMood.getTasks().values()) {
                for (int type : taskBlockTypes(task.getType())) {
                    wantedTypes.add(type);
                }
            }
            if (!wantedTypes.isEmpty()) {
                Map<Integer, List<Vec3>> byType = new HashMap<>();
                for (var entry : NoellesrolesClient.taskBlocks.entrySet()) {
                    int type = entry.getValue();
                    if (wantedTypes.contains(type)) {
                        byType.computeIfAbsent(type, k -> new ArrayList<>())
                                .add(Vec3.atCenterOf(entry.getKey()));
                    }
                }
                for (var entry : byType.entrySet()) {
                    Color color = typeColor(entry.getKey());
                    for (Vec3 center : mergeClusters(entry.getValue())) {
                        targets.add(new Target(center, color));
                    }
                }
            }
        }

        // 手持钥匙 → 指向自己房间的门（复用透视的钥匙匹配逻辑与颜色）
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(TMMItems.KEY)) {
            ItemLore lore = mainHand.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                String roomNumber = lore.lines().getFirst().getString();
                List<Vec3> doors = new ArrayList<>();
                for (BlockPos doorPos : TaskBlockOverlayRenderer.RoomDoorPositions) {
                    if (client.level.getBlockEntity(doorPos) instanceof SmallDoorBlockEntity door
                            && door.getKeyName().equals(roomNumber)) {
                        doors.add(Vec3.atCenterOf(doorPos));
                    }
                }
                Color doorColor = new Color(255, 247, 155);
                for (Vec3 center : mergeClusters(doors)) {
                    targets.add(new Target(center, doorColor));
                }
            }
        }
        return targets;
    }

    /** 贪心单链聚类：距离在 {@link #MERGE_DISTANCE} 内的点合并为一簇，返回各簇中心。 */
    private static List<Vec3> mergeClusters(List<Vec3> points) {
        List<List<Vec3>> clusters = new ArrayList<>();
        for (Vec3 point : points) {
            List<Vec3> joined = null;
            for (List<Vec3> cluster : clusters) {
                boolean near = false;
                for (Vec3 member : cluster) {
                    if (point.distanceTo(member) <= MERGE_DISTANCE) {
                        near = true;
                        break;
                    }
                }
                if (!near)
                    continue;
                if (joined == null) {
                    cluster.add(point);
                    joined = cluster;
                } else {
                    // point 同时接近多个簇 → 桥接合并
                    joined.addAll(cluster);
                    cluster.clear();
                }
            }
            if (joined == null) {
                List<Vec3> cluster = new ArrayList<>();
                cluster.add(point);
                clusters.add(cluster);
            }
        }
        List<Vec3> centers = new ArrayList<>();
        for (List<Vec3> cluster : clusters) {
            if (cluster.isEmpty())
                continue;
            Vec3 sum = Vec3.ZERO;
            for (Vec3 member : cluster) {
                sum = sum.add(member);
            }
            centers.add(sum.scale(1.0 / cluster.size()));
        }
        return centers;
    }

    /** 在玩家头顶绘制一支指向目标的 3D 箭头（透视线，任意相机视角可见）。 */
    private static void drawArrow(PoseStack matrices, VertexConsumer vertexConsumer,
            Vec3 origin, Vec3 cameraPos, Target target) {
        Vec3 delta = target.position().subtract(origin);
        if (delta.lengthSqr() < 1.0e-4)
            return;
        Vec3 dir = delta.normalize();
        Vec3 upRef = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(upRef).normalize();
        Vec3 side2 = dir.cross(side).normalize();
        Vec3 tip = dir.scale(ARROW_LENGTH);
        Vec3 headBase = dir.scale(ARROW_LENGTH - ARROW_HEAD);

        float r = target.color().getRed() / 255f;
        float g = target.color().getGreen() / 255f;
        float b = target.color().getBlue() / 255f;

        matrices.pushPose();
        matrices.translate(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        line(pose, vertexConsumer, Vec3.ZERO, tip, r, g, b);
        double spread = ARROW_HEAD * 0.7;
        line(pose, vertexConsumer, tip, headBase.add(side.scale(spread)), r, g, b);
        line(pose, vertexConsumer, tip, headBase.add(side.scale(-spread)), r, g, b);
        line(pose, vertexConsumer, tip, headBase.add(side2.scale(spread)), r, g, b);
        line(pose, vertexConsumer, tip, headBase.add(side2.scale(-spread)), r, g, b);
        matrices.popPose();
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertexConsumer,
            Vec3 from, Vec3 to, float r, float g, float b) {
        Vec3 normal = to.subtract(from).normalize();
        vertexConsumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, 1f)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertexConsumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, 1f)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** SAN 任务类型 → 任务点方块类型 ID（与 {@link TaskBlockOverlayRenderer#render} 的映射一致）。 */
    private static int[] taskBlockTypes(SREPlayerTaskComponent.Task task) {
        return switch (task) {
            case EAT -> new int[] { 1 };
            case DRINK -> new int[] { 2 };
            case BATHE -> new int[] { 3 };
            case SLEEP -> new int[] { 4 };
            case EXERCISE -> new int[] { 5 };
            case RAED_BOOK -> new int[] { 6 };
            case TOILET -> new int[] { 8 };
            case CHAIR -> new int[] { 9 };
            case NOTE_BLOCK -> new int[] { 10 };
            case LIGHT_STOVE -> new int[] { 16 };
            case CLEAN_DUST -> new int[] { 17 };
            case TRANSPORT -> new int[] { 18, 19 };
            case PRAY -> new int[] { 20 };
            case PRUNE_BUSH -> new int[] { 21 };
            case HARVEST_CROP -> new int[] { 22 };
            default -> new int[0];
        };
    }

    /** 任务点方块类型 ID → 透视颜色（与 {@link TaskBlockOverlayRenderer#render} 的颜色一致）。 */
    private static Color typeColor(int type) {
        return switch (type) {
            case 1 -> Color.GREEN;
            case 2 -> new Color(234, 88, 88);
            case 3 -> new Color(141, 234, 189);
            case 4 -> new Color(0, 255, 220);
            case 5 -> new Color(255, 242, 0);
            case 6 -> new Color(255, 127, 39);
            case 8 -> new Color(255, 174, 201);
            case 9 -> new Color(126, 255, 228);
            case 10 -> new Color(121, 148, 255);
            case 16 -> new Color(255, 165, 0);
            case 17 -> new Color(192, 192, 192);
            case 18 -> new Color(0, 150, 50);
            case 19 -> new Color(180, 40, 40);
            case 20 -> new Color(255, 255, 180);
            case 21 -> new Color(173, 255, 47);
            case 22 -> new Color(218, 165, 32);
            default -> Color.WHITE;
        };
    }

    private record Target(Vec3 position, Color color) {
    }
}
