    package io.wifi.starrailexpress.client.gui.screen;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;

import java.util.*;
import java.awt.Color;

import static net.minecraft.client.renderer.blockentity.BeaconRenderer.BEAM_LOCATION;

public class WaypointHUD {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final List<WaypointMarker> waypoints = new ArrayList<>();
    private static boolean waypointsVisible = true;
    private static final Set<String> hiddenWaypoints = new HashSet<>(); // 存储被隐藏的特定路径点，格式为"path/name"


    public static class WaypointMarker {
        public BlockPos pos;
        public String name;
        public String path;  // 添加路径属性
        public Color color;
        public boolean visible = true;
        public boolean showBeam = true;
        public boolean showDistance = true;
        public boolean showLabel = true;
        public boolean showGroundMarker = true;
        public boolean showBlockHighlight = false; // 是否高亮方块
        public float beamHeight = 64.0f;
        public float beamThickness = 0.2f;

        public WaypointMarker(BlockPos pos, String name, String path, Color color) {
            this.pos = pos;
            this.name = name;
            this.path = path;
            this.color = color;
        }
    }

    // 添加/移除路径点的方法
    public static void addWaypoint(BlockPos pos, String name, Color color) {
        // 为了向后兼容，假设路径为"default"
        waypoints.add(new WaypointMarker(pos, name, "default", color));
    }

    public static void addWaypoint(BlockPos pos, String name, String path, Color color) {
        waypoints.add(new WaypointMarker(pos, name, path, color));
    }

    public static void addWaypoint(BlockPos pos, String name, Color color, float beamHeight) {
        WaypointMarker marker = new WaypointMarker(pos, name, "default", color); // 为了向后兼容
        marker.beamHeight = beamHeight;
        waypoints.add(marker);
    }

    public static void removeWaypoint(String name) {
        waypoints.removeIf(marker -> marker.name.equals(name));
    }

    public static void clearAllWaypoints() {
        waypoints.clear();
    }

    public static List<WaypointMarker> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public static void setWaypointsVisible(boolean visible) {
        waypointsVisible = visible;
    }

    public static boolean areWaypointsVisible() {
        return waypointsVisible;
    }

    // 显示所有路径点
    public static void showWaypoints() {
        waypointsVisible = true;
    }

    // 隐藏所有路径点
    public static void hideWaypoints() {
        waypointsVisible = false;
    }

    // 显示特定路径点
    public static void showSpecificWaypoint(String path, String name) {
        waypointsVisible = true; // 确保整体可见性开启
        String fullPath = path + "/" + name;
        hiddenWaypoints.remove(fullPath); // 从隐藏列表中移除
        for (WaypointMarker marker : waypoints) {
            if (marker.path.equals(path) && marker.name.equals(name)) {
                marker.visible = true;
            }
        }
    }

    // 隐藏特定路径点
    public static void hideSpecificWaypoint(String path, String name) {
        String fullPath = path + "/" + name;
        hiddenWaypoints.add(fullPath); // 添加到隐藏列表
        for (WaypointMarker marker : waypoints) {
            if (marker.path.equals(path) && marker.name.equals(name)) {
                marker.visible = false;
            }
        }
    }

    // 主要渲染方法，在世界渲染阶段调用
    public static void renderWorldMarkers(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick,GuiGraphics guiGraphics) {
        if (!waypointsVisible || mc.player == null || mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // 按距离排序（从远到近渲染，确保近处的标记覆盖远处的）
        List<WaypointMarker> sortedWaypoints = new ArrayList<>(waypoints);
        sortedWaypoints.sort((a, b) -> Double.compare(
                b.pos.distSqr(mc.player.blockPosition()),
                a.pos.distSqr(mc.player.blockPosition())
        ));

        for (WaypointMarker marker : sortedWaypoints) {
            if (marker.visible && shouldRenderMarker(marker, cameraPos)) {
                renderMarkerInWorld(poseStack, bufferSource, marker, cameraPos, partialTick, guiGraphics);
            }
        }
    }
    private static void renderBeaconBeam(PoseStack poseStack, MultiBufferSource multiBufferSource, float f, long l, int i, int j, int k) {
        BeaconRenderer.renderBeaconBeam(poseStack, multiBufferSource, BEAM_LOCATION, f, 1.0F, l, i, j, k, 0.2F, 0.25F);
    }

    private static boolean shouldRenderMarker(WaypointMarker marker, Vec3 cameraPos) {
        // 计算距离
        double distance = cameraPos.distanceTo(Vec3.atCenterOf(marker.pos));

        // 根据距离决定是否渲染（可选）
        return distance < 256.0; // 只在256格内渲染
    }

    private static void renderMarkerInWorld(PoseStack poseStack, MultiBufferSource bufferSource,
                                            WaypointMarker marker, Vec3 cameraPos, float partialTick, GuiGraphics guiGraphics) {
        poseStack.pushPose();

        // 获取标记的世界坐标
        Vec3 markerPos = Vec3.atCenterOf(marker.pos);
        double renderX = markerPos.x - cameraPos.x;
        double renderY = markerPos.y - cameraPos.y;
        double renderZ = markerPos.z - cameraPos.z;

        poseStack.translate(renderX, renderY, renderZ);
        renderBeaconBeam(poseStack,bufferSource, partialTick, Minecraft.getInstance().level.getGameTime(),0,((int) marker.beamHeight), marker.color.getRGB());
        // 渲染光
        if (marker.showBeam) {
            renderBeam(poseStack, bufferSource, marker, partialTick);
        }

        // 渲染地面标记
        if (marker.showGroundMarker) {
            renderGroundMarker(poseStack, bufferSource, marker, partialTick);
        }

        // 渲染方块高亮
        if (marker.showBlockHighlight) {
            renderBlockHighlight(poseStack, bufferSource, marker, partialTick);
        }

        poseStack.popPose();

        // 渲染标签（在标签渲染阶段）
        if (marker.showLabel) {
            renderLabel(guiGraphics,marker, partialTick);
        }
    }

    private static void renderBeam(PoseStack poseStack, MultiBufferSource bufferSource,
                                   WaypointMarker marker, float partialTick) {
        float[] rgb = marker.color.getRGBColorComponents(null);
        float red = rgb[0];
        float green = rgb[1];
        float blue = rgb[2];
        float alpha = 0.7f;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.beaconBeam(BEAM_LOCATION, true));
        Matrix4f matrix = poseStack.last().pose();

        // 光束参数
        float height = marker.beamHeight;
        float thickness = marker.beamThickness;
        float halfThickness = thickness / 2.0f;

        // 动画：光束脉动
        long time = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * Mth.sin(time * 0.001f);
        float animatedHeight = height * (0.8f + 0.2f * pulse);

        // 绘制多层光束效果
        for (int layer = 0; layer < 3; layer++) {
            float layerOffset = layer * 0.05f; // 层间距
            float layerAlpha = alpha * (0.6f - layer * 0.15f); // 外层透明度更低
            float layerThickness = halfThickness + layerOffset;

            for (int i = 0; i < 4; i++) {
                float angle = (float)i * Mth.PI / 2.0f;
                float dx = Mth.cos(angle) * layerThickness;
                float dz = Mth.sin(angle) * layerThickness;

                // 每个面由两个三角形组成
                vertexConsumer.addVertex(matrix, -dx, 0, -dz)
                        .setColor(red, green, blue, layerAlpha)
                        .setUv(0, 1)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(15728880);

                vertexConsumer.addVertex(matrix, -dx, animatedHeight, -dz)
                        .setColor(red, green, blue, layerAlpha * 0.3f)
                        .setUv(1, 1)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(15728880);

                vertexConsumer.addVertex(matrix, dx, animatedHeight, dz)
                        .setColor(red, green, blue, layerAlpha * 0.3f)
                        .setUv(1, 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(15728880);

                vertexConsumer.addVertex(matrix, dx, 0, dz)
                        .setColor(red, green, blue, layerAlpha)
                        .setUv(0, 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(15728880);
            }
        }

        // 绘制光束顶部的光晕
        renderBeamTopGlow(poseStack, bufferSource, marker, animatedHeight, partialTick);
    }

    private static void renderBeamTopGlow(PoseStack poseStack, MultiBufferSource bufferSource,
                                          WaypointMarker marker, float height, float partialTick) {
        poseStack.pushPose();
        poseStack.translate(0, height, 0);

        float[] rgb = marker.color.getRGBColorComponents(null);
        float red = rgb[0];
        float green = rgb[1];
        float blue = rgb[2];
        float alpha = 0.4f;

        // 创建圆形光晕
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        int segments = 32; // 增加段数使圆形更平滑
        float radius = 0.8f;

        // 第一层光晕
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)i * 2 * Mth.PI / segments;
            float angle2 = (float)(i + 1) * 2 * Mth.PI / segments;

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // 三角形扇形
            vertexConsumer.addVertex(matrix, 0, 0.01f, 0)
                    .setColor(red, green, blue, alpha)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x1, 0.01f, z1)
                    .setColor(red, green, blue, alpha * 0.5f)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x2, 0.01f, z2)
                    .setColor(red, green, blue, alpha * 0.5f)
                    .setNormal(0, 1, 0);
        }
        
        // 第二层光晕（较小且更透明）
        float innerRadius = 0.5f;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float)i * 2 * Mth.PI / segments;
            float angle2 = (float)(i + 1) * 2 * Mth.PI / segments;

            float x1 = Mth.cos(angle1) * innerRadius;
            float z1 = Mth.sin(angle1) * innerRadius;
            float x2 = Mth.cos(angle2) * innerRadius;
            float z2 = Mth.sin(angle2) * innerRadius;

            vertexConsumer.addVertex(matrix, 0, 0.02f, 0) // 稍微高一点避免z-fighting
                    .setColor(red, green, blue, alpha * 0.7f)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x1, 0.02f, z1)
                    .setColor(red, green, blue, alpha * 0.3f)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x2, 0.02f, z2)
                    .setColor(red, green, blue, alpha * 0.3f)
                    .setNormal(0, 1, 0);
        }

        poseStack.popPose();
    }

    private static void renderBlockHighlight(PoseStack poseStack, MultiBufferSource bufferSource,
                                           WaypointMarker marker, float partialTick) {
        float[] rgb = marker.color.getRGBColorComponents(null);
        float red = rgb[0];
        float green = rgb[1];
        float blue = rgb[2];
        float alpha = 0.4f; // 透明度

        // 获取世界中的方块边界
        if (mc.level != null) {
            var blockState = mc.level.getBlockState(marker.pos);
            var shape = blockState.getShape(mc.level, marker.pos);
            
            if (!shape.isEmpty()) {
                var bounds = shape.bounds();
                VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
                Matrix4f matrix = poseStack.last().pose();

                // 应用动画效果
                long time = System.currentTimeMillis();
                float pulse = 0.2f + 0.1f * Mth.sin(time * 0.002f); // 较慢的脉动效果
                float offset = pulse; // 微微扩大边框

                // 绘制方块轮廓线
                // 底面四条线
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                // 顶面四条线
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                // 四个垂直边
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.minZ - offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.maxX + offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);

                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.minY - offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
                vertexConsumer.addVertex(matrix, (float)bounds.minX - offset, (float)bounds.maxY + offset, (float)bounds.maxZ + offset)
                        .setColor(red, green, blue, alpha)
                        .setNormal(0, 1, 0);
            }
        }
    }

    private static void renderGroundMarker(PoseStack poseStack, MultiBufferSource bufferSource,
                                           WaypointMarker marker, float partialTick) {
        float[] rgb = marker.color.getRGBColorComponents(null);
        float red = rgb[0];
        float green = rgb[1];
        float blue = rgb[2];

        // 使用lines渲染类型绘制线框
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);
        Matrix4f matrix = poseStack.last().pose();

        // 绘制外圆
        int circleSegments = 32;
        float circleRadius = 1.0f;
        float yOffset = 0.02f; // 略高于地面

        for (int i = 0; i < circleSegments; i++) {
            float angle1 = (float)i * 2 * Mth.PI / circleSegments;
            float angle2 = (float)(i + 1) * 2 * Mth.PI / circleSegments;

            float x1 = Mth.cos(angle1) * circleRadius;
            float z1 = Mth.sin(angle1) * circleRadius;
            float x2 = Mth.cos(angle2) * circleRadius;
            float z2 = Mth.sin(angle2) * circleRadius;

            // 绘制线段
            vertexConsumer.addVertex(matrix, x1, yOffset, z1)
                    .setColor(red, green, blue, 1.0f)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x2, yOffset, z2)
                    .setColor(red, green, blue, 1.0f)
                    .setNormal(0, 1, 0);
        }

        // 绘制第二个同心圆
        float innerRadius = 0.8f;
        for (int i = 0; i < circleSegments; i++) {
            float angle1 = (float)i * 2 * Mth.PI / circleSegments;
            float angle2 = (float)(i + 1) * 2 * Mth.PI / circleSegments;

            float x1 = Mth.cos(angle1) * innerRadius;
            float z1 = Mth.sin(angle1) * innerRadius;
            float x2 = Mth.cos(angle2) * innerRadius;
            float z2 = Mth.sin(angle2) * innerRadius;

            vertexConsumer.addVertex(matrix, x1, yOffset, z1)
                    .setColor(red, green, blue, 0.6f)
                    .setNormal(0, 1, 0);

            vertexConsumer.addVertex(matrix, x2, yOffset, z2)
                    .setColor(red, green, blue, 0.6f)
                    .setNormal(0, 1, 0);
        }

        // 绘制十字线
        float crossSize = 0.9f;
        float borderWidth = 0.08f;
        
        // 水平线
        vertexConsumer.addVertex(matrix, -crossSize, yOffset, -borderWidth)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, crossSize, yOffset, -borderWidth)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, crossSize, yOffset, borderWidth)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, -crossSize, yOffset, borderWidth)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        
        // 垂直线
        vertexConsumer.addVertex(matrix, -borderWidth, yOffset, -crossSize)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, borderWidth, yOffset, -crossSize)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, borderWidth, yOffset, crossSize)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, -borderWidth, yOffset, crossSize)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);

        // 绘制箭头方向指示
        renderDirectionIndicator(poseStack, bufferSource, marker, partialTick);
    }

    private static void renderDirectionIndicator(PoseStack poseStack, MultiBufferSource bufferSource,
                                                 WaypointMarker marker, float partialTick) {
        if (mc.player == null) return;

        // 计算从玩家到标记的方向
        Vec3 playerPos = mc.player.position();
        Vec3 markerPos = Vec3.atCenterOf(marker.pos);
        Vec3 direction = markerPos.subtract(playerPos).normalize();

        // 计算水平方向角度
        float angle = (float)Math.atan2(direction.z, direction.x);

        poseStack.pushPose();
        poseStack.translate(0, 0.05f, 0); // 略高于地面标记
        poseStack.mulPose(new Quaternionf().rotateY(-angle));

        float[] rgb = marker.color.getRGBColorComponents(null);
        float red = rgb[0];
        float green = rgb[1];
        float blue = rgb[2];

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.LINES);
        Matrix4f matrix = poseStack.last().pose();

        // 绘制方向箭头
        float arrowSize = 0.4f;

        // 箭头主线（带边框效果）
        float borderWidth = 0.02f;
        
        // 箭头主体
        vertexConsumer.addVertex(matrix, 0, 0, 0)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, arrowSize, 0, 0)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);

        // 箭头头部
        float headSize = 0.15f;
        vertexConsumer.addVertex(matrix, arrowSize - headSize, -headSize, 0)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, arrowSize, 0, 0)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, arrowSize - headSize, headSize, 0)
                .setColor(red, green, blue, 1.0f)
                .setNormal(0, 1, 0);
        
        // 添加箭头边框
        vertexConsumer.addVertex(matrix, 0-borderWidth, -borderWidth, 0)
                .setColor(0.0f, 0.0f, 0.0f, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, arrowSize+borderWidth, -borderWidth, 0)
                .setColor(0.0f, 0.0f, 0.0f, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, arrowSize+borderWidth, borderWidth, 0)
                .setColor(0.0f, 0.0f, 0.0f, 1.0f)
                .setNormal(0, 1, 0);
        vertexConsumer.addVertex(matrix, 0-borderWidth, borderWidth, 0)
                .setColor(0.0f, 0.0f, 0.0f, 1.0f)
                .setNormal(0, 1, 0);

        poseStack.popPose();
    }

    private static void renderLabel(GuiGraphics guiGraphics, WaypointMarker marker, float partialTick) {
        if (mc.player == null || mc.font == null) return;

        Vec3 markerPos = Vec3.atCenterOf(marker.pos);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double distance = cameraPos.distanceTo(markerPos);

        // 距离过远不渲染标签
        if (distance > 64.0) return;

        // 计算标签位置（在标记上方）
        Vec3 labelPos = markerPos.add(0, marker.beamHeight + 2.0, 0);

        // 将3D坐标转换为屏幕坐标
        PoseStack poseStack = new PoseStack();

        // 获取相机信息
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 transformed = labelPos.subtract(cameraPos);

        // 检查是否在相机前方
        final var lookVector = camera.getLookVector();
        if (transformed.dot(new Vec3(lookVector.x, lookVector.y, lookVector.z)) <= 0) {
            return;
        }

        // 简单的投影计算
        double scale = 0.1 / transformed.length();
        double screenX = mc.getWindow().getGuiScaledWidth() / 2.0 + transformed.x * scale * 1000;
        double screenY = mc.getWindow().getGuiScaledHeight() / 2.0 - transformed.y * scale * 1000;

        // 渲染标签（在GUI层渲染）
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // 确保标签在屏幕范围内
        if (screenX >= -50 && screenX <= screenWidth + 50 && screenY >= -50 && screenY <= screenHeight + 50) {
            // 创建标签文本
            String labelText = marker.name + " (" + String.format("%.0fm", distance) + ")";
            int textWidth = mc.font.width(labelText);
            int textHeight = mc.font.lineHeight;
            
            // 计算标签位置
            int labelX = (int) screenX - textWidth / 2;
            int labelY = (int) screenY - textHeight / 2;
            
            // 绘制半透明背景
            int backgroundColor = 0x80000000; // 半透明白色背景
            int borderColor = marker.color.getRGB();
            
            // 背景矩形
            guiGraphics.fill(labelX - 4, labelY - 2, labelX + textWidth + 4, labelY + textHeight + 2, backgroundColor);
            
            // 边框
            guiGraphics.fill(labelX - 5, labelY - 3, labelX + textWidth + 5, labelY - 2, borderColor);
            guiGraphics.fill(labelX - 5, labelY + textHeight + 2, labelX + textWidth + 5, labelY + textHeight + 3, borderColor);
            guiGraphics.fill(labelX - 5, labelY - 3, labelX - 4, labelY + textHeight + 3, borderColor);
            guiGraphics.fill(labelX + textWidth + 4, labelY - 3, labelX + textWidth + 5, labelY + textHeight + 3, borderColor);
            
            // 文本
            guiGraphics.drawString(mc.font, labelText, labelX, labelY, marker.color.getRGB(), false);
        }
    }

    // HUD渲染方法（在GUI层渲染屏幕上的指示器）
    public static void renderHUD(GuiGraphics guiGraphics, float partialTick) {
        if (!waypointsVisible || mc.player == null || mc.level == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 渲染屏幕上的方向指示器
        for (WaypointMarker marker : waypoints) {
            if (marker.visible) {
                renderScreenIndicator(guiGraphics, marker, screenWidth, screenHeight, partialTick);
                
                // 如果标签可见，则渲染标签
                if (marker.showLabel) {
                    renderLabel(guiGraphics, marker, partialTick);
                }
            }
        }
    }

    private static void renderScreenIndicator(GuiGraphics guiGraphics, WaypointMarker marker,
                                              int screenWidth, int screenHeight, float partialTick) {
        if (mc.player == null) return;

        Vec3 playerPos = mc.player.getEyePosition(partialTick);
        Vec3 markerPos = Vec3.atCenterOf(marker.pos);

        // 计算方向向量
        Vec3 direction = markerPos.subtract(playerPos);
        double distance = direction.length();

        // 检查是否在视野内
        Vec3 lookVec = mc.player.getLookAngle();
        double dotProduct = direction.normalize().dot(lookVec);

        // 计算屏幕边缘指示器位置
       // if (dotProduct < 0.3) { // 不在视野前方，显示边缘指示器
            float angle = (float)Math.atan2(direction.z, direction.x);
            angle -= mc.player.getYRot() * Mth.DEG_TO_RAD;
            angle += Mth.PI; // 调整角度

            // 计算边缘位置
            int margin = 30;
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            int radius = Math.min(centerX, centerY) - margin;

            int edgeX = (int)(centerX + Math.cos(angle) * radius);
            int edgeY = (int)(centerY + Math.sin(angle) * radius);

            // 限制在屏幕内
            edgeX = Mth.clamp(edgeX, margin, screenWidth - margin);
            edgeY = Mth.clamp(edgeY, margin, screenHeight - margin);

            // 绘制边缘指示器
            drawEdgeIndicator(guiGraphics, edgeX, edgeY, angle, marker.color, distance);
        //}
    }

    private static void drawEdgeIndicator(GuiGraphics guiGraphics, int x, int y,
                                          float angle, Color color, double distance) {
        int size = 12;
        int colorRGB = color.getRGB();
        int outlineColor = 0xFF000000; // 黑色边框

        // 绘制方向箭头（带边框）
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        int tipX = (int)(x + cos * size);
        int tipY = (int)(y + sin * size);

        int leftX = (int)(x + cos * (-size/2) + sin * (size/2));
        int leftY = (int)(y + sin * (-size/2) - cos * (size/2));

        int rightX = (int)(x + cos * (-size/2) - sin * (size/2));
        int rightY = (int)(y + sin * (-size/2) + cos * (size/2));

        // 绘制黑色边框
        guiGraphics.fill(tipX-1, tipY-1, leftX-1, leftY-1, outlineColor);
        guiGraphics.fill(tipX-1, tipY-1, rightX-1, rightY-1, outlineColor);
        guiGraphics.fill(leftX-1, leftY-1, rightX-1, rightY-1, outlineColor);

        guiGraphics.fill(tipX+1, tipY+1, leftX+1, leftY+1, outlineColor);
        guiGraphics.fill(tipX+1, tipY+1, rightX+1, rightY+1, outlineColor);
        guiGraphics.fill(leftX+1, leftY+1, rightX+1, rightY+1, outlineColor);

        guiGraphics.fill(tipX, tipY, leftX, leftY, colorRGB);
        guiGraphics.fill(tipX, tipY, rightX, rightY, colorRGB);
        guiGraphics.fill(leftX, leftY, rightX, rightY, colorRGB);

        // 绘制距离文本背景
        String distText = String.format("%.0fm", distance);
        int textWidth = mc.font.width(distText) + 6;
        int textHeight = mc.font.lineHeight + 4;
        int textX = x - textWidth / 2;
        int textY = y + 15;

        // 绘制半透明背景矩形
        guiGraphics.fill(textX - 2, textY - 2, textX + textWidth - 2, textY + textHeight - 2, 0x80000000);
        guiGraphics.fill(textX - 1, textY - 1, textX + textWidth - 1, textY + textHeight - 1, 0x80FFFFFF);
        
        // 绘制距离文本
        guiGraphics.drawString(mc.font, distText, textX + 2, textY + 2, colorRGB, false);
    }

    // 工具方法：快速添加颜色路径点
    public static void addRedWaypoint(BlockPos pos, String name) {
        addWaypoint(pos, name, Color.RED, 32.0f);
    }

    public static void addGreenWaypoint(BlockPos pos, String name) {
        addWaypoint(pos, name, Color.GREEN, 32.0f);
    }

    public static void addBlueWaypoint(BlockPos pos, String name) {
        addWaypoint(pos, name, Color.BLUE, 32.0f);
    }

    public static void addYellowWaypoint(BlockPos pos, String name) {
        addWaypoint(pos, name, Color.YELLOW, 32.0f);
    }

    // 临时路径点（一段时间后自动移除）
    public static void addTemporaryWaypoint(BlockPos pos, String name, Color color, int durationTicks) {
        WaypointMarker marker = new WaypointMarker(pos, name, "default", color); // 为了向后兼容
        waypoints.add(marker);

        // 在实际使用中，您可能需要一个定时器来移除临时路径点
        // 这里只是添加，移除逻辑需要额外实现
    }
}