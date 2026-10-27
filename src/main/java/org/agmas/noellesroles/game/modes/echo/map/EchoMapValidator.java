package org.agmas.noellesroles.game.modes.echo.map;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

import java.util.*;
import java.util.function.Function;

/** 只校验结构和几何约束；真实世界碰撞与路线需单独检查。 */
public final class EchoMapValidator {
    private EchoMapValidator() { }

    /** 按阵营出生点计算连续可支持的人数上限；不代替结构/世界校验。 */
    public static int spawnCapacity(EchoMapConfig map) {
        int capacity = EchoRosterPolicy.MIN_PLAYERS - 1;
        for (int count = EchoRosterPolicy.MIN_PLAYERS; count <= EchoRosterPolicy.MAX_PLAYERS; count++) {
            var profile = EchoRosterPolicy.forPlayerCount(count);
            if (map.carrierSpawns().size() < profile.carriers()
                    || map.interceptorSpawns().size() < profile.interceptors()) break;
            capacity = count;
        }
        return capacity;
    }

    /** 保存/绑定至少检查最低人数档；旧图只检查到自身出生点容量。 */
    public static int validationPlayerLimit(EchoMapConfig map) {
        return Math.max(EchoRosterPolicy.MIN_PLAYERS, spawnCapacity(map));
    }

    public static List<String> validate(EchoMapConfig map, int count) {
        var profile = EchoRosterPolicy.forPlayerCount(count);
        List<String> errors = new ArrayList<>();
        unique(map.beacons(), EchoMapConfig.Beacon::id, "beacons", errors);
        unique(map.crystalNodes(), EchoMapConfig.Node::id, "crystalNodes", errors);
        unique(map.altars(), EchoMapConfig.Point::id, "altars", errors);
        unique(map.supplyCrates(), EchoMapConfig.Crate::id, "supplyCrates", errors);
        unique(map.blackCrystalNodes(), EchoMapConfig.Point::id, "blackCrystalNodes", errors);
        unique(map.blackDropoffs(), EchoMapConfig.Point::id, "blackDropoffs", errors);
        unique(map.coreGate().doors(), EchoMapConfig.Door::id, "coreGate.doors", errors);
        require(map.carrierSpawns().size() >= Math.max(8, profile.carriers()) && map.carrierSpawns().size() <= 128, "carrierSpawns数量", errors);
        require(map.interceptorSpawns().size() >= Math.max(2, profile.interceptors()) && map.interceptorSpawns().size() <= 128, "interceptorSpawns数量", errors);
        // brokerSpawns仅保留旧文件兼容；中立复用carrier池，不增加旧图人数限制。
        require(map.brokerSpawns().size() <= 128, "brokerSpawns超过128项", errors);
        require(map.altars().size() == 3, "altars必须恰好三个", errors);
        require(map.coreGate().doors().size() >= 2, "核心区至少两个入口", errors);
        require(map.blackCrystalNodes().size() >= 2 && map.blackDropoffs().size() >= 2, "黑晶点/投放点不足", errors);
        var beacons = map.beacons().stream().filter(p -> p.minPlayers() <= count).toList();
        require(beacons.size() == profile.beacons(), "beacons与人数档不符", errors);
        require(beacons.stream().filter(p -> p.region().equals("core")).count() == 1, "必需核心信标应为一座", errors);
        for (var beacon : beacons) require(map.coreArea().contains(beacon.pos()) == beacon.region().equals("core"), "beacons."+beacon.id()+"区域不匹配", errors);
        var nodes = map.crystalNodes().stream().filter(p -> p.minPlayers() <= count).toList();
        require(nodes.size() >= (count <= 7 ? 4 : 6), "开放晶簇数量不足", errors);
        for (var node : nodes) openPoint(map, node.pos(), "crystalNodes."+node.id(), errors);
        var crates = map.supplyCrates().stream().filter(p -> p.minPlayers() <= count).toList();
        long common = crates.stream().filter(p -> p.quality().equals("COMMON") && p.repeatable()).count();
        long black = crates.stream().filter(p -> p.quality().equals("BLACK_MARKET") && !p.repeatable()).count();
        require(common >= (count <= 7 ? 6 : count == 8 ? 8 : 10), "开放物资箱数量不足", errors);
        require(black >= 2, "核心黑市箱数量不足", errors);
        for (var crate : crates) {
            if (crate.quality().equals("COMMON")) {
                require(crate.repeatable(), "普通物资箱必须可恢复: "+crate.id(), errors);
                openPoint(map,crate.pos(),"supplyCrates."+crate.id(),errors);
            } else {
                require(!crate.repeatable() && map.coreArea().contains(crate.pos()), "黑市箱必须在核心且每局一次: "+crate.id(), errors);
                require(!blocked(map,crate.pos()),"黑市箱落在门体/撤离区: "+crate.id(),errors);
            }
        }
        for (var point : map.altars()) openPoint(map,point.pos(),"altars."+point.id(),errors);
        for (var point : map.blackCrystalNodes()) openPoint(map,point.pos(),"blackCrystalNodes."+point.id(),errors);
        for (var point : map.blackDropoffs()) openPoint(map,point.pos(),"blackDropoffs."+point.id(),errors);
        openPoint(map,map.brokerExit().pos(),"brokerExit",errors);
        List<EchoMapConfig.Spawn> others = new ArrayList<>(map.carrierSpawns());
        for (var spawn : others) openPoint(map,spawn.pos(),"spawn",errors);
        for (var wolf : map.interceptorSpawns()) {
            openPoint(map,wolf.pos(),"interceptorSpawn",errors);
            for (var other : others) require(wolf.pos().distanceTo(other.pos()) >= 24, "对立出生点距离小于24",errors);
        }
        List<EchoMapConfig.Spawn> spawns = new ArrayList<>(others);
        spawns.addAll(map.interceptorSpawns());
        for (int a = 0; a < spawns.size(); a++) for (int b = a + 1; b < spawns.size(); b++) {
            var first = spawns.get(a);
            var second = spawns.get(b);
            require(Math.abs(first.x() - second.x()) >= 0.6 || Math.abs(first.z() - second.z()) >= 0.6
                    || Math.abs(first.y() - second.y()) >= 1.8, "出生点互相重叠", errors);
        }
        pairDistance(beacons.stream().map(EchoMapConfig.Beacon::pos).toList(),24,"beacons",errors);
        pairDistance(map.altars().stream().map(EchoMapConfig.Point::pos).toList(),20,"altars",errors);
        List<EchoMapConfig.Box> doorBoxes = new ArrayList<>();
        for (var door : map.coreGate().doors()) doorBoxes.add(door.volume());
        doorBoxes.add(map.evacuation().doorVolume());
        require(doorBoxes.stream().allMatch(EchoMapConfig.Box::integral),"门体AABB必须为整数",errors);
        require(doorBoxes.stream().mapToDouble(EchoMapConfig.Box::volume).sum() <= 1024,"门体总体积超过1024",errors);
        for (int a=0;a<doorBoxes.size();a++) for(int b=a+1;b<doorBoxes.size();b++) {
            require(!doorBoxes.get(a).intersects(doorBoxes.get(b)),"门体互相重叠",errors);
        }
        for (var door : map.coreGate().doors()) require(!door.volume().intersects(map.evacuation().zone()),"核心门体与撤离区重叠",errors);
        require(!map.evacuation().doorVolume().intersects(map.evacuation().zone()),"撤离门体与检测区重叠",errors);
        var free = map.coreGate().freeExit();
        require(map.coreArea().contains(free.controlPos()),"免费出口控制点必须在核心内部",errors);
        openPoint(map,free.outsideSafe().pos(),"freeExit.outsideSafe",errors);
        openPoint(map,map.evacuation().outsideSafe().pos(),"evacuation.outsideSafe",errors);
        openPoint(map,map.evacuation().exitDropAnchor(),"evacuation.exitDropAnchor",errors);
        return List.copyOf(errors);
    }

    private static boolean blocked(EchoMapConfig map, EchoMapConfig.Vec pos) {
        return map.evacuation().zone().contains(pos) || map.evacuation().doorVolume().contains(pos)
                || map.coreGate().doors().stream().anyMatch(d -> d.volume().contains(pos));
    }
    private static void openPoint(EchoMapConfig map, EchoMapConfig.Vec point, String field, List<String> errors) {
        require(!map.coreArea().contains(point) && !blocked(map,point),field+"必须在开放区且不在门体/撤离区",errors);
    }
    private static void pairDistance(List<EchoMapConfig.Vec> points, int minimum, String field, List<String> errors) {
        for(int a=0;a<points.size();a++) for(int b=a+1;b<points.size();b++) {
            require(points.get(a).distanceTo(points.get(b))>=minimum,field+"点间距不足",errors);
        }
    }
    private static <T> void unique(List<T> entries, Function<T,String> id, String field, List<String> errors) {
        require(entries.size()<=128,field+"超过128项",errors);
        Set<String> seen = new HashSet<>();
        for(T entry:entries) require(seen.add(id.apply(entry)),field+"重复ID: "+id.apply(entry),errors);
    }
    private static void require(boolean valid, String message, List<String> errors) { if(!valid) errors.add(message); }
}
