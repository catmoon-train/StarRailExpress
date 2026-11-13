package org.agmas.noellesroles.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 不初始化 Minecraft/渲染器：执行距离策略，并检查世界读取与修复点的调用顺序。 */
class TaskBlockOverlayRendererPerformanceTest {
    @Test void bothModesKeepInclusiveDistanceBoundariesOnEveryAxis() {
        for (boolean ultra : new boolean[] { false, true }) {
            int radius = ultra ? 64 : 128;
            int maxDistanceSquared = TaskBlockOverlayVisibility.maxDistanceSquared(ultra);
            assertEquals(radius * radius, maxDistanceSquared);
            for (int axis = 0; axis < 3; axis++) {
                for (int sign : new int[] { -1, 1 }) {
                    assertFalse(outsideAlongAxis(axis, sign * (radius - 0.0001), maxDistanceSquared));
                    assertFalse(outsideAlongAxis(axis, sign * (double) radius, maxDistanceSquared));
                    assertTrue(outsideAlongAxis(axis, sign * (radius + 0.0001), maxDistanceSquared));
                }
            }
        }
    }

    @Test void distanceUsesBlockCenterAndSphericalCameraDistance() {
        int normal = TaskBlockOverlayVisibility.maxDistanceSquared(false);
        int ultra = TaskBlockOverlayVisibility.maxDistanceSquared(true);
        assertFalse(TaskBlockOverlayVisibility.isOutsideDistance(128, 0, 0, 0.5, 0.5, 0.5, normal));
        assertTrue(TaskBlockOverlayVisibility.isOutsideDistance(128, 0, 0, 0, 0.5, 0.5, normal));
        assertFalse(TaskBlockOverlayVisibility.isOutsideDistance(64, 0, 0, 0.5, 0.5, 0.5, ultra));
        assertTrue(TaskBlockOverlayVisibility.isOutsideDistance(64, 0, 0, 0, 0.5, 0.5, ultra));
        assertTrue(TaskBlockOverlayVisibility.isOutsideDistance(96, 96, 0, 0.5, 0.5, 0.5, normal));
        assertTrue(TaskBlockOverlayVisibility.isOutsideDistance(48, 48, 0, 0.5, 0.5, 0.5, ultra));
    }

    @Test void taskLoopCullsBeforeWorldReadAndReusesFrameCameraAndMode() throws Exception {
        String source = rendererSource();
        String render = between(source, "public static void render(WorldRenderContext renderContext)",
                "private static boolean isActiveSabotageRepairBlock");
        String loop = render.substring(render.indexOf("for (var set : NoellesrolesClient.taskBlocks.entrySet())"));
        int stateRead = loop.indexOf("BlockState block = renderContext.world().getBlockState(pos);");
        assertTrue(stateRead > 0);
        String beforeRead = compact(loop.substring(0, stateRead));
        assertTrue(beforeRead.contains("if (TaskBlockOverlayVisibility.isOutsideDistance(pos.getX(), pos.getY(), pos.getZ(), "
                + "cameraPos.x, cameraPos.y, cameraPos.z, maxRenderDistance)) { continue; }"));
        assertFalse(beforeRead.contains("shouldDisplay["));
        assertFalse(beforeRead.contains("isTaskInstinctTypeShowable("));
        assertEquals(stateRead + "BlockState block = ".length(), loop.indexOf("renderContext.world().getBlockState(pos)"));
        assertTrue(render.contains("Vec3 cameraPos = renderContext.camera().getPosition();"));
        assertTrue(compact(render).contains("int maxRenderDistance = TaskBlockOverlayVisibility.maxDistanceSquared( "
                + "SREClientConfig.instance().isUltraPerfMode());"));
        String executableLoop = compact(loop);
        assertFalse(executableLoop.contains("Minecraft.getInstance()"));
        assertFalse(executableLoop.contains("SREClientConfig.instance()"));
        assertFalse(executableLoop.contains(".camera()"));

        String publicEntry = compact(between(source, "public static void renderBlockOverlay(",
                "private static void renderVisibleBlockOverlay("));
        assertTrue(publicEntry.contains("if (world == null) return;"));
        assertTrue(publicEntry.contains("if (TaskBlockOverlayVisibility.isOutsideDistance(blockPos.getX(), "
                + "blockPos.getY(), blockPos.getZ(), cameraPos.x, cameraPos.y, cameraPos.z, maxRenderDistance)) { return; }"));
        assertTrue(publicEntry.contains("renderVisibleBlockOverlay(context, blockPos, color, alpha, world, cameraPos);"));
    }

    @Test void activeRepairBlocksStillBypassTaskTypeAndCustomVisibilityGates() throws Exception {
        String source = rendererSource();
        String loop = between(source, "for (var set : NoellesrolesClient.taskBlocks.entrySet())", "switch (type)");
        String repairBranch = compact(loop.substring(loop.indexOf("if (isActiveSabotageRepairBlock(block))")));
        assertFalse(repairBranch.contains("shouldDisplay["));
        assertFalse(repairBranch.contains("isTaskInstinctTypeShowable("));
        assertFalse(repairBranch.contains("shouldRenderTaskInstinct("));
        assertTrue(repairBranch.contains("it.taskInstinctRenderColor(block, pos, player)"));
        assertTrue(repairBranch.contains("float alpha = c.getAlpha() / 255f;"));
        assertTrue(repairBranch.contains("renderVisibleBlockOverlay(renderContext, pos, c, alpha, world, cameraPos); continue;"));

        String repairPredicate = compact(between(source, "private static boolean isActiveSabotageRepairBlock",
                "private static boolean isBellMeetingEnabled"));
        for (String block : new String[] { "ReactorBlock", "WaterValveBlock", "DebrisPileBlock" }) {
            assertTrue(repairPredicate.contains("if (state.getBlock() instanceof " + block + ") { "
                    + "return state.getValue(" + block + ".ACTIVE) && !state.getValue(" + block + ".CLOSED); }"));
        }
        assertTrue(repairPredicate.endsWith("return false; }"));
    }

    private static boolean outsideAlongAxis(int axis, double distance, int maxDistanceSquared) {
        return TaskBlockOverlayVisibility.isOutsideDistance(-21, 64, 8,
                -20.5 + (axis == 0 ? distance : 0),
                64.5 + (axis == 1 ? distance : 0),
                8.5 + (axis == 2 ? distance : 0), maxDistanceSquared);
    }

    private static String rendererSource() throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles/client/TaskBlockOverlayRenderer.java"));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, end);
        return source.substring(from, to);
    }

    private static String compact(String source) {
        return source.replaceAll("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/", " ").replaceAll("\\s+", " ").trim();
    }
}
