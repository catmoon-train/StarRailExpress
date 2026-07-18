package net.exmo.sre.sixtyseconds.client;

import net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapS2CPacket;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.client.map.StarMapManager;
import org.agmas.noellesroles.client.map.StarRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端星图数据持有器：缓存服务端最近一次下发的 {@link SixtySecondsStarMapS2CPacket}，
 * 把其中的星级区域条目转成 {@link StarRegion} 注入 {@link StarMapManager}，让全屏星图
 * 与 HUD 小地图能绘制星级边框、标签与所在区域指示。
 * <p>
 * 数据流：客户端发 {@code SixtySecondsStarMapRequestC2SPacket} 请求 → 服务端回
 * {@code SixtySecondsStarMapS2CPacket} → 本类 {@link #accept} 转换并注入 StarMapManager。
 */
public final class SixtySecondsClientStarMap {

    private static SixtySecondsStarMapS2CPacket data;

    private SixtySecondsClientStarMap() {
    }

    /** 网络接收（主线程）：转换星级区域并注入 StarMapManager。 */
    public static void accept(SixtySecondsStarMapS2CPacket packet) {
        data = packet;
        List<StarRegion> regions = new ArrayList<>();
        if (packet.regions() != null) {
            for (SixtySecondsStarMapS2CPacket.RegionEntry r : packet.regions()) {
                // AABB 的 Y 范围给个全高度，因为 StarRegion.contains(worldX, 0, worldZ) 只查 X/Z
                AABB bounds = new AABB(r.minX(), 0, r.minZ(), r.maxX() + 1, 256, r.maxZ() + 1);
                String name = r.name() == null || r.name().isEmpty()
                        ? ("R" + (regions.size() + 1)) : r.name();
                regions.add(StarRegion.of(r.level(), name, bounds));
            }
        }
        StarMapManager.setStarRegions(regions);
    }

    public static SixtySecondsStarMapS2CPacket data() {
        return data;
    }

    public static void reset() {
        data = null;
        StarMapManager.setStarRegions(null);
    }
}
