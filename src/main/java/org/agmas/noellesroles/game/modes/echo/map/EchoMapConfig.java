package org.agmas.noellesroles.game.modes.echo.map;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

import java.util.List;
import java.util.Objects;

/** 地图侧文件的不可变数据；所有坐标均一次性从模板平移到实战区域。 */
public record EchoMapConfig(int schemaVersion, String mapId, String coordinateSpace, Box coreArea,
                            List<Spawn> carrierSpawns, List<Spawn> interceptorSpawns, List<Spawn> brokerSpawns,
                            List<Beacon> beacons, List<Node> crystalNodes, List<Point> altars, List<Crate> supplyCrates,
                            CoreGate coreGate, Evacuation evacuation, List<Point> blackCrystalNodes,
                            List<Point> blackDropoffs, Point brokerExit, Spawn spectatorPos) {
    public EchoMapConfig {
        if (schemaVersion != 1) throw new IllegalArgumentException("schemaVersion必须为1");
        text(mapId); text(coordinateSpace);
        if (!coordinateSpace.equals("template") && !coordinateSpace.equals("world")) throw new IllegalArgumentException("未知坐标系");
        Objects.requireNonNull(coreArea); Objects.requireNonNull(coreGate); Objects.requireNonNull(evacuation);
        Objects.requireNonNull(brokerExit); Objects.requireNonNull(spectatorPos);
        carrierSpawns = List.copyOf(carrierSpawns); interceptorSpawns = List.copyOf(interceptorSpawns);
        brokerSpawns = List.copyOf(brokerSpawns); beacons = List.copyOf(beacons); crystalNodes = List.copyOf(crystalNodes);
        altars = List.copyOf(altars); supplyCrates = List.copyOf(supplyCrates);
        blackCrystalNodes = List.copyOf(blackCrystalNodes); blackDropoffs = List.copyOf(blackDropoffs);
    }

    public EchoMapConfig translated(Vec offset) {
        if (!coordinateSpace.equals("template")) throw new IllegalStateException("地图坐标已转换");
        return new EchoMapConfig(schemaVersion, mapId, "world", coreArea.translate(offset),
                carrierSpawns.stream().map(p -> p.translate(offset)).toList(),
                interceptorSpawns.stream().map(p -> p.translate(offset)).toList(),
                brokerSpawns.stream().map(p -> p.translate(offset)).toList(),
                beacons.stream().map(p -> new Beacon(p.id, p.pos.add(offset), p.region, p.minPlayers)).toList(),
                crystalNodes.stream().map(p -> new Node(p.id, p.pos.add(offset), p.minPlayers)).toList(),
                altars.stream().map(p -> p.translate(offset)).toList(),
                supplyCrates.stream().map(p -> new Crate(p.id, p.pos.add(offset), p.minPlayers, p.quality, p.repeatable)).toList(),
                new CoreGate(coreGate.doors.stream().map(p -> new Door(p.id, p.controlPos.add(offset), p.volume.translate(offset))).toList(),
                        new FreeExit(coreGate.freeExit.id, coreGate.freeExit.controlPos.add(offset), coreGate.freeExit.outsideSafe.translate(offset))),
                new Evacuation(evacuation.controlPos.add(offset), evacuation.doorVolume.translate(offset), evacuation.zone.translate(offset),
                        evacuation.outsideSafe.translate(offset), evacuation.exitDropAnchor.add(offset)),
                blackCrystalNodes.stream().map(p -> p.translate(offset)).toList(), blackDropoffs.stream().map(p -> p.translate(offset)).toList(),
                brokerExit.translate(offset), spectatorPos.translate(offset));
    }

    public record Vec(double x, double y, double z) {
        public Vec { finite(x); finite(y); finite(z); }
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public double distanceTo(Vec other) { return Math.sqrt(Math.pow(x-other.x,2)+Math.pow(y-other.y,2)+Math.pow(z-other.z,2)); }
    }
    public record Spawn(double x, double y, double z, float yaw, float pitch) {
        public Spawn { finite(x); finite(y); finite(z); finite(yaw); finite(pitch); if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch越界"); }
        public Vec pos() { return new Vec(x,y,z); }
        public Spawn translate(Vec offset) { return new Spawn(x+offset.x,y+offset.y,z+offset.z,yaw,pitch); }
    }
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            finite(minX); finite(minY); finite(minZ); finite(maxX); finite(maxY); finite(maxZ);
            if (minX >= maxX || minY >= maxY || minZ >= maxZ) throw new IllegalArgumentException("无效AABB");
        }
        public boolean contains(Vec p) { return p.x>=minX && p.x<maxX && p.y>=minY && p.y<maxY && p.z>=minZ && p.z<maxZ; }
        public boolean intersects(Box b) { return minX<b.maxX && maxX>b.minX && minY<b.maxY && maxY>b.minY && minZ<b.maxZ && maxZ>b.minZ; }
        public Box translate(Vec p) { return new Box(minX+p.x,minY+p.y,minZ+p.z,maxX+p.x,maxY+p.y,maxZ+p.z); }
        public double volume() { return (maxX-minX)*(maxY-minY)*(maxZ-minZ); }
        public boolean integral() { return minX==Math.rint(minX)&&minY==Math.rint(minY)&&minZ==Math.rint(minZ)
                && maxX==Math.rint(maxX)&&maxY==Math.rint(maxY)&&maxZ==Math.rint(maxZ); }
    }
    public record Point(String id, Vec pos) {
        public Point { text(id); Objects.requireNonNull(pos); }
        public Point translate(Vec p) { return new Point(id,pos.add(p)); }
    }
    public record Node(String id, Vec pos, int minPlayers) {
        public Node { text(id); Objects.requireNonNull(pos); threshold(minPlayers); }
    }
    public record Beacon(String id, Vec pos, String region, int minPlayers) {
        public Beacon {
            text(id); Objects.requireNonNull(pos); threshold(minPlayers);
            if (!"core".equals(region) && !"open".equals(region)) throw new IllegalArgumentException("未知信标region");
        }
    }
    public record Crate(String id, Vec pos, int minPlayers, String quality, boolean repeatable) {
        public Crate {
            text(id); Objects.requireNonNull(pos); threshold(minPlayers);
            if (!"COMMON".equals(quality) && !"BLACK_MARKET".equals(quality)) throw new IllegalArgumentException("未知物资箱品质");
        }
    }
    public record Door(String id, Vec controlPos, Box volume) {
        public Door { text(id); Objects.requireNonNull(controlPos); Objects.requireNonNull(volume); }
    }
    public record FreeExit(String id, Vec controlPos, Spawn outsideSafe) {
        public FreeExit { text(id); Objects.requireNonNull(controlPos); Objects.requireNonNull(outsideSafe); }
    }
    public record CoreGate(List<Door> doors, FreeExit freeExit) {
        public CoreGate { doors=List.copyOf(doors); Objects.requireNonNull(freeExit); }
    }
    public record Evacuation(Vec controlPos, Box doorVolume, Box zone, Spawn outsideSafe, Vec exitDropAnchor) {
        public Evacuation { Objects.requireNonNull(controlPos); Objects.requireNonNull(doorVolume); Objects.requireNonNull(zone);
            Objects.requireNonNull(outsideSafe); Objects.requireNonNull(exitDropAnchor); }
    }
    private static void finite(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new IllegalArgumentException("坐标非有限值或越界");
    }
    private static void text(String text) {
        if (text == null || text.isBlank() || text.length()>128) throw new IllegalArgumentException("缺失或超长ID");
    }
    private static void threshold(int count) { if (count<EchoRosterPolicy.MIN_PLAYERS || count>EchoRosterPolicy.MAX_PLAYERS) throw new IllegalArgumentException("minPlayers必须为6–32"); }
}
