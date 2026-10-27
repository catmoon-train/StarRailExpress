package org.agmas.noellesroles.game.modes.echo.map;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView.Type;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** 纯公共配置投影：只读实战坐标，不访问实体、目标运行状态或任何阵营数据。 */
public final class EchoFacilityMapSnapshot {
    private EchoFacilityMapSnapshot() { }

    /** 同类编号按配置顺序从 1 开始，仅统计本局人数已启用的设施。 */
    public static List<EchoFacilityMapPayload.Entry> project(EchoMapConfig map, int playerCount) {
        if (!"world".equals(map.coordinateSpace())) throw new IllegalArgumentException("设施地图需要实战坐标");
        if (playerCount < EchoRosterPolicy.MIN_PLAYERS || playerCount > EchoRosterPolicy.MAX_PLAYERS)
            throw new IllegalArgumentException("设施地图人数越界");
        var entries = new ArrayList<EchoFacilityMapPayload.Entry>();
        var numbers = new EnumMap<Type, Integer>(Type.class);
        for (var p : map.beacons()) if (p.minPlayers() <= playerCount)
            add(entries, numbers, "core".equals(p.region()) ? Type.CORE_BEACON : Type.BEACON, p.pos());
        for (var p : map.crystalNodes()) if (p.minPlayers() <= playerCount)
            add(entries, numbers, Type.CRYSTAL, p.pos());
        for (var p : map.altars()) add(entries, numbers, Type.ALTAR, p.pos());
        for (var p : map.supplyCrates()) if (p.minPlayers() <= playerCount)
            add(entries, numbers, "BLACK_MARKET".equals(p.quality()) ? Type.BLACK_MARKET : Type.SUPPLY, p.pos());
        for (var p : map.coreGate().doors()) add(entries, numbers, Type.CORE_CONSOLE, p.controlPos());
        add(entries, numbers, Type.FREE_EXIT, map.coreGate().freeExit().controlPos());
        add(entries, numbers, Type.EVACUATION_CONSOLE, map.evacuation().controlPos());
        var zone = map.evacuation().zone();
        // 现有枚举没有独立 EVACUATION；撤离控制台为 #1，撤离区中心复用该类型为 #2。
        add(entries, numbers, Type.EVACUATION_CONSOLE, new EchoMapConfig.Vec(
                (zone.minX() + zone.maxX()) / 2, (zone.minY() + zone.maxY()) / 2, (zone.minZ() + zone.maxZ()) / 2));
        for (var p : map.blackCrystalNodes()) add(entries, numbers, Type.BLACK_NODE, p.pos());
        for (var p : map.blackDropoffs()) add(entries, numbers, Type.BLACK_DROPOFF, p.pos());
        add(entries, numbers, Type.BROKER_EXIT, map.brokerExit().pos());
        return List.copyOf(entries);
    }

    private static void add(List<EchoFacilityMapPayload.Entry> entries, EnumMap<Type, Integer> numbers,
                            Type type, EchoMapConfig.Vec pos) {
        // 侧文件七个设施数组各至多128项，加四个单点，宽松上界900，低于2048；异常配置不可截断。
        if (entries.size() >= EchoFacilityMapPayload.MAX_ENTRIES)
            throw new IllegalArgumentException("设施地图点位超过2048项，无法生成完整快照");
        int number = numbers.merge(type, 1, Integer::sum);
        entries.add(new EchoFacilityMapPayload.Entry(type, pos.x(), pos.y(), pos.z(), number));
    }
}
