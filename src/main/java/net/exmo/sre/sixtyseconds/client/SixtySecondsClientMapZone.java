package net.exmo.sre.sixtyseconds.client;

import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 60s 区域地图的客户端状态：当前应扫描的<b>地图区域</b>（住宅/避难所/探索区，由服务端在
 * 传送/进出探索区时推送）+ <b>自己家的点位</b> + 玩家<b>自定义标注</b>。
 * <p>
 * 生效时（{@link #isActive()}）：{@code AreaMapManager} 改扫本区域而非全图 playArea；
 * {@code AreaMapScreen}/{@code AreaMapHud} 不再画任务点，改画家点位与标注。
 * 标注为世界绝对坐标、跨区域保留（不在当前区域内的自然不显示），断线/清区时清空。
 */
public final class SixtySecondsClientMapZone {
    /** 标注颜色循环表（ARGB）。 */
    public static final int[] MARKER_COLORS = {
            0xFFE05C5C, 0xFF5CB8E0, 0xFF7CE08A, 0xFFE0C05C, 0xFFC08AE0, 0xFFE08AB8 };
    private static final int MAX_MARKERS = 32;

    /** 一个玩家自定义标注（世界坐标）。 */
    public record Marker(double worldX, double worldZ, int color) {
    }

    private static AABB zone;
    private static BlockPos homePos;
    private static boolean inSafeZone = false;
    private static List<BlockPos> shelterDoors = List.of();
    private static final List<Marker> MARKERS = new ArrayList<>();
    private static int nextColor = 0;

    private SixtySecondsClientMapZone() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearZone();
            MARKERS.clear();
            nextColor = 0;
        });
    }

    /** 60s 模式进行中且服务端已推送区域。 */
    public static boolean isActive() {
        return zone != null && SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SixtySecondsMod.MODE != null
                && SREClient.gameComponent.getGameMode() == SixtySecondsMod.MODE;
    }

    public static AABB activeZone() {
        return isActive() ? zone : null;
    }

    public static BlockPos homePos() {
        return isActive() ? homePos : null;
    }

    public static boolean isInSafeZone() {
        return inSafeZone;
    }

    /** 所有队伍的避难所门位置（创造模式观察用，由服务端 {@code SixtySecondsMapZoneS2CPacket#shelterDoors} 推送）。 */
    public static List<BlockPos> shelterDoors() {
        return isActive() ? shelterDoors : List.of();
    }

    public static void setZone(AABB newZone, BlockPos home, boolean safe) {
        setZone(newZone, home, safe, List.of());
    }

    public static void setZone(AABB newZone, BlockPos home, boolean safe, List<BlockPos> doors) {
        zone = newZone;
        homePos = home;
        inSafeZone = safe;
        shelterDoors = doors;
    }

    public static void setZone(AABB newZone, BlockPos home) {
        setZone(newZone, home, false);
    }

    public static void clearZone() {
        zone = null;
        homePos = null;
        inSafeZone = false;
        shelterDoors = List.of();
    }

    // ── 自定义标注 ─────────────────────────────────────────────
    public static List<Marker> markers() {
        return MARKERS;
    }

    /** 添加一个标注（颜色循环；超上限丢弃最旧的）。 */
    public static void addMarker(double worldX, double worldZ) {
        MARKERS.add(new Marker(worldX, worldZ, MARKER_COLORS[nextColor++ % MARKER_COLORS.length]));
        if (MARKERS.size() > MAX_MARKERS) {
            MARKERS.remove(0);
        }
    }

    // ── 尸体标记（自动复活；服务端经 SixtySecondsCorpseMarkS2CPacket 推送）──────────
    /** 尸体标记的固定颜色：暗红，与玩家自定义标注的循环色明显区分。 */
    public static final int CORPSE_COLOR = 0xFF8B1A1A;
    /** 当前尸体标记（复用同一张标注表与渲染，只是颜色固定、由服务端增删）。 */
    private static Marker corpseMarker = null;

    /** 死亡时打上尸体标记（同时只会有一个：新的顶掉旧的）。 */
    public static void setCorpseMarker(double worldX, double worldZ) {
        clearCorpseMarker();
        corpseMarker = new Marker(worldX, worldZ, CORPSE_COLOR);
        MARKERS.add(corpseMarker);
        if (MARKERS.size() > MAX_MARKERS) {
            MARKERS.remove(0);
        }
    }

    /** 复活后清除尸体标记；标注表被挤爆时它可能已被淘汰，remove 无匹配也无妨。 */
    public static void clearCorpseMarker() {
        if (corpseMarker != null) {
            MARKERS.remove(corpseMarker);
            corpseMarker = null;
        }
    }

    /** 队友标点颜色（金色/橙色暖色调），与自定义标注循环色区分。 */
    public static final int PING_COLOR = 0xFFFFAA00;

    /** 添加一个队友标点标记（固定金色，不占颜色循环序号；超上限丢弃最旧的）。 */
    public static void addPingMarker(double worldX, double worldZ) {
        MARKERS.add(new Marker(worldX, worldZ, PING_COLOR));
        if (MARKERS.size() > MAX_MARKERS) {
            MARKERS.remove(0);
        }
    }

    /** 移除离 (worldX, worldZ) 最近且距离 ≤ maxDist 的标注；返回是否移除了。 */
    public static boolean removeMarkerNear(double worldX, double worldZ, double maxDist) {
        Marker best = null;
        double bestSqr = maxDist * maxDist;
        for (Marker marker : MARKERS) {
            double dx = marker.worldX() - worldX;
            double dz = marker.worldZ() - worldZ;
            double sqr = dx * dx + dz * dz;
            if (sqr <= bestSqr) {
                bestSqr = sqr;
                best = marker;
            }
        }
        return best != null && MARKERS.remove(best);
    }
}
