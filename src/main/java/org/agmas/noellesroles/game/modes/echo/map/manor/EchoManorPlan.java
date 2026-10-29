package org.agmas.noellesroles.game.modes.echo.map.manor;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapValidator;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Beacon;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Box;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Crate;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Door;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Node;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Point;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.SpawnPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@link EchoManorLayout} 翻译成回声模式侧文件（模板坐标）。
 *
 * <p>坐标转换只做一次：布局局部坐标 + 模板基准点 = 模板绝对坐标。
 * 之后由 {@code EchoMapConfig.translated(offset)} 统一平移到实战区，
 * 生成器不再自己加第二次偏移。
 */
public final class EchoManorPlan {
    /** 默认地图 id：同时用作 Areas 地图名与侧文件名。 */
    public static final String DEFAULT_MAP_ID = "season1/echo_manor";

    private EchoManorPlan() { }

    /** 模板基准点：布局局部 (0,0,0) 对应的模板绝对坐标。 */
    public record Base(int x, int y, int z) { }

    private static EchoMapConfig.Vec vec(Base base, int x, int z) {
        return new EchoMapConfig.Vec(base.x() + x + 0.5D, base.y() + EchoManorLayout.FEET_Y, base.z() + z + 0.5D);
    }

    private static EchoMapConfig.Vec vecAt(Base base, int x, int y, int z) {
        return new EchoMapConfig.Vec(base.x() + x + 0.5D, base.y() + y, base.z() + z + 0.5D);
    }

    private static EchoMapConfig.Vec feet(Base base, SpawnPoint point) {
        return vec(base, point.x(), point.z());
    }

    private static EchoMapConfig.Box box(Base base, Box box) {
        return new EchoMapConfig.Box(base.x() + box.minX(), base.y() + box.minY(), base.z() + box.minZ(),
                base.x() + box.maxX(), base.y() + box.maxY(), base.z() + box.maxZ());
    }

    /** 控制台高度与真实 part=0 同源，不能继续沿用旧嵌墙面板的 y=5。 */
    private static EchoMapConfig.Vec machineRoot(Base base, Layout layout, int x, int z) {
        var cell = layout.machineAt(x, EchoManorLayout.FEET_Y, z);
        if (cell == null || cell.part() != 0) throw new IllegalArgumentException("缺少机器根点: " + x + "," + z);
        return vecAt(base, x, cell.machine().root().y(), z);
    }

    /** 生成侧文件配置；调用方负责再跑 {@link #validate} 与落盘。 */
    public static EchoMapConfig toConfig(Layout layout, String mapId, Base base) throws IOException {
        EchoMapDraft draft = EchoMapDraft.empty(mapId);
        for (SpawnPoint point : layout.carriers()) draft.mark("carrier_spawn", "", feet(base, point), point.yaw(), 0.0F, 6);
        for (SpawnPoint point : layout.interceptors()) draft.mark("interceptor_spawn", "", feet(base, point), point.yaw(), 0.0F, 6);
        for (SpawnPoint point : layout.brokers()) draft.mark("broker_spawn", "", feet(base, point), point.yaw(), 0.0F, 6);
        // 观战点放在中央广场正上方、核心大厅屋顶以下，既能看到全图又不卡在屋顶里。
        draft.mark("spectator", "spectator", vecAt(base, layout.spectator().x(), 12, layout.spectator().z()),
                layout.spectator().yaw(), 30.0F, 6);

        for (Beacon beacon : layout.beacons())
            draft.mark(beacon.region().equals("core") ? "beacon_core" : "beacon_open", beacon.id(),
                    vec(base, beacon.x(), beacon.z()), 0.0F, 0.0F, beacon.minPlayers());
        for (Node node : layout.crystals())
            draft.mark("crystal", node.id(), vec(base, node.x(), node.z()), 0.0F, 0.0F, node.minPlayers());
        for (Point altar : layout.altars())
            draft.mark("altar", altar.id(), vec(base, altar.x(), altar.z()), 0.0F, 0.0F, 6);
        for (Crate crate : layout.crates())
            draft.mark(crate.quality().equals("BLACK_MARKET") ? "black_market" : "supply", crate.id(),
                    vec(base, crate.x(), crate.z()), 0.0F, 0.0F, 6);
        for (Point node : layout.blackNodes())
            draft.mark("black_node", node.id(), vec(base, node.x(), node.z()), 0.0F, 0.0F, 6);
        for (Point drop : layout.blackDropoffs())
            draft.mark("black_dropoff", drop.id(), vec(base, drop.x(), drop.z()), 0.0F, 0.0F, 6);
        draft.mark("broker_exit", layout.brokerExit().id(), vec(base, layout.brokerExit().x(), layout.brokerExit().z()), 0.0F, 0.0F, 6);
        draft.mark("free_control", layout.freeControl().id(), vec(base, layout.freeControl().x(), layout.freeControl().z()), 0.0F, 0.0F, 6);
        draft.mark("free_safe", "free_safe", feet(base, layout.freeSafe()), layout.freeSafe().yaw(), 0.0F, 6);
        draft.mark("evacuation_control", layout.evacControl().id(), machineRoot(base, layout, layout.evacControl().x(), layout.evacControl().z()), 0.0F, 0.0F, 6);
        draft.mark("evacuation_safe", "evacuation_safe", feet(base, layout.evacSafe()), layout.evacSafe().yaw(), 0.0F, 6);
        draft.mark("drop_anchor", "drop_anchor", vec(base, layout.dropAnchor().x(), layout.dropAnchor().z()), 0.0F, 0.0F, 6);

        for (Door door : layout.doors()) {
            draft.mark("core_control", door.id(), machineRoot(base, layout, door.panelX(), door.panelZ()), 0.0F, 0.0F, 6);
            draft.volume("core_door", door.id(), box(base, door.volume()));
        }
        draft.volume("core_area", "main", box(base, layout.coreArea()));
        draft.volume("evacuation_door", "main", box(base, layout.evacDoor()));
        draft.volume("evacuation_zone", "main", box(base, layout.evacZone()));
        return draft.exported(mapId);
    }

    /** 6–32 人全部人数档的结构校验；生成庄园必须支持最高人数。 */
    public static List<String> validate(EchoMapConfig config) {
        List<String> issues = new ArrayList<>();
        for (int count = EchoRosterPolicy.MIN_PLAYERS; count <= EchoRosterPolicy.MAX_PLAYERS; count++) {
            for (String issue : EchoMapValidator.validate(config, count)) issues.add("N=" + count + ": " + issue);
        }
        return List.copyOf(issues);
    }
}
