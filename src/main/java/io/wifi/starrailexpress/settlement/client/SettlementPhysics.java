package io.wifi.starrailexpress.settlement.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 纯数学的三质点刚性胶囊：固定步长、重力、约束、空间散列碰撞及带摩擦的地面反弹。 */
public final class SettlementPhysics {
    private static final double RADIUS = .34, SPACING = .54;
    public final List<Body> bodies = new ArrayList<>();
    private final Map<Long, List<Node>> grid = new HashMap<>();

    public static final class Node {
        public double x, y, z, px, py, pz, ox, oy, oz;
        private final Body owner;
        Node(Body owner, double x, double y, double z) {
            this.owner = owner;
            this.x = px = ox = x; this.y = py = oy = y; this.z = pz = oz = z;
        }
        public double x(double alpha) { return ox + (x - ox) * alpha; }
        public double y(double alpha) { return oy + (y - oy) * alpha; }
        public double z(double alpha) { return oz + (z - oz) * alpha; }
    }

    public static final class Body {
        public final Node[] nodes = new Node[3];
        public boolean active;
        public final double twist;
        public Body(double x, double y, double z, int seed) {
            twist = seed * 2.399963229728653;
            for (int i = 0; i < 3; i++) nodes[i] = new Node(this, x + (i - 1) * .08,
                    y + (i - 1) * SPACING, z);
        }
        public void release(double x, double y, double z) {
            active = true;
            for (int i = 0; i < 3; i++) {
                Node n = nodes[i];
                n.x = n.ox = x + (i - 1) * .18 * Math.cos(twist);
                n.y = n.oy = y + (i - 1) * .5;
                n.z = n.oz = z + (i - 1) * .18 * Math.sin(twist);
                n.px = n.x - .035 * Math.cos(twist);
                n.py = n.y; n.pz = n.z - .035 * Math.sin(twist);
            }
        }
    }

    public void tick() {
        for (Body b : bodies) if (b.active) for (Node n : b.nodes) {
            n.ox = n.x; n.oy = n.y; n.oz = n.z;
        }
        // 两次子步使高速落体不穿过地板；不使用真实时间或补帧循环。
        for (int step = 0; step < 2; step++) {
            for (Body b : bodies) if (b.active) for (Node n : b.nodes) {
                double vx = (n.x - n.px) * .994, vy = (n.y - n.py) * .994,
                        vz = (n.z - n.pz) * .994;
                n.px = n.x; n.py = n.y; n.pz = n.z;
                n.x += vx; n.y += vy - .02; n.z += vz;
            }
            for (int iteration = 0; iteration < 5; iteration++) {
                for (Body b : bodies) if (b.active) {
                    constrain(b.nodes[0], b.nodes[1], SPACING);
                    constrain(b.nodes[1], b.nodes[2], SPACING);
                    constrain(b.nodes[0], b.nodes[2], SPACING * 2);
                }
                collide();
                for (Body b : bodies) if (b.active) for (Node n : b.nodes) floor(n);
            }
        }
    }

    private static void constrain(Node a, Node b, double length) {
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-8) return;
        double s = (d - length) / d * .5;
        a.x += dx * s; a.y += dy * s; a.z += dz * s;
        b.x -= dx * s; b.y -= dy * s; b.z -= dz * s;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    private void collide() {
        grid.clear();
        for (Body body : bodies) if (body.active) for (Node n : body.nodes) {
            int x = (int) Math.floor(n.x), y = (int) Math.floor(n.y), z = (int) Math.floor(n.z);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    var bucket = grid.get(key(x + dx, y + dy, z + dz));
                    if (bucket == null) continue;
                    for (Node other : bucket) if (other.owner != body) contact(n, other);
                }
            grid.computeIfAbsent(key(x, y, z), k -> new ArrayList<>()).add(n);
        }
    }

    private static void contact(Node a, Node b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 >= 4 * RADIUS * RADIUS) return;
        if (d2 < 1e-10) { dx = .0001; dy = .0001; d2 = dx * dx + dy * dy; }
        double d = Math.sqrt(d2), nx = dx / d, ny = dy / d, nz = dz / d;
        double push = (2 * RADIUS - d) * .5;
        double avx = a.x-a.px, avy = a.y-a.py, avz = a.z-a.pz;
        double bvx = b.x-b.px, bvy = b.y-b.py, bvz = b.z-b.pz;
        a.x += nx*push; a.y += ny*push; a.z += nz*push;
        b.x -= nx*push; b.y -= ny*push; b.z -= nz*push;
        // 位置投影不能被下一子步当作额外速度，否则尸体会被约束“弹射”到四周。
        double rvx=avx-bvx, rvy=avy-bvy, rvz=avz-bvz;
        double normal=rvx*nx+rvy*ny+rvz*nz;
        double impulse=Math.max(0,-normal)*.52;
        double tx=rvx-normal*nx, ty=rvy-normal*ny, tz=rvz-normal*nz;
        double tangent=Math.sqrt(tx*tx+ty*ty+tz*tz);
        double friction=tangent<1e-8 ? 0 : Math.min(.5, (impulse*.8+push*.2)/tangent);
        double ix=nx*impulse-tx*friction, iy=ny*impulse-ty*friction, iz=nz*impulse-tz*friction;
        a.px=a.x-avx-ix; a.py=a.y-avy-iy; a.pz=a.z-avz-iz;
        b.px=b.x-bvx+ix; b.py=b.y-bvy+iy; b.pz=b.z-bvz+iz;
    }

    private static void floor(Node n) {
        if (n.y < RADIUS) {
            double vy = n.y - n.py, vx = n.x - n.px, vz = n.z - n.pz;
            n.y = RADIUS;
            n.py = n.y + Math.min(0, vy) * (Math.abs(vy) > .035 ? .28 : 0);
            n.px = n.x - vx * .78; n.pz = n.z - vz * .78;
        }
        // 后台承接槽与舞台黑石矮墙一致；高于矮墙的尸体仍能自由翻转和相互碰撞。
        if (n.y < 2 + RADIUS) {
            double x = Math.max(-1.16, Math.min(1.16,n.x));
            double z = Math.max(4.84, Math.min(7.16,n.z));
            if (x != n.x) { n.x = x; n.px = x; }
            if (z != n.z) { n.z = z; n.pz = z; }
        }
        n.x = Math.max(-14.5, Math.min(14.5,n.x));
        n.z = Math.max(2.5, Math.min(13.5,n.z));
    }
}
