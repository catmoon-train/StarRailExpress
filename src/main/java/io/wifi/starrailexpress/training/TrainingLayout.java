package io.wifi.starrailexpress.training;

import com.google.gson.Gson;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 内置布局同时提供传送点、活动边界和轻量导航节点。 */
public final class TrainingLayout {
    private static final TrainingLayout INSTANCE = load();
    public int version;
    public double[] bounds;
    public List<Area> areas;
    public static TrainingLayout get() { return INSTANCE; }
    public Area area(String id) {
        return areas.stream().filter(a -> a.id.equals(id)).findFirst().orElseGet(() ->
                areas.stream().filter(a -> a.id.equals("range")).findFirst().orElseThrow());
    }
    private static TrainingLayout load() {
        var stream = TrainingLayout.class.getResourceAsStream("/data/starrailexpress/training/layout.json");
        if (stream == null) throw new IllegalStateException("Missing training layout");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, TrainingLayout.class);
        } catch (Exception e) { throw new IllegalStateException("Invalid training layout", e); }
    }
    public static Vec3 point(double[] p) { return new Vec3(p[0], p[1], p[2]); }
    public static AABB box(double[] b) { return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]); }
    public static final class Area {
        public String id;
        public double[] bounds;
        public double[] spawn;
        public List<double[]> botSpawns;
        public List<double[]> patrol;
        public List<double[]> nodes;
        public AABB box() { return TrainingLayout.box(bounds); }
        public Vec3 spawn() { return point(spawn); }
        public float yaw() { return spawn.length > 3 ? (float) spawn[3] : 0; }
        public Vec3 botSpawn(int slot) {
            return botSpawns == null || botSpawns.isEmpty() ? spawn() : point(botSpawns.get(Math.floorMod(slot, botSpawns.size())));
        }
        public List<Vec3> patrolPoints() { return patrol == null ? List.of() : patrol.stream().map(TrainingLayout::point).toList(); }
        public List<Vec3> nodePoints() { return nodes == null ? List.of() : nodes.stream().map(TrainingLayout::point).toList(); }
    }
}
