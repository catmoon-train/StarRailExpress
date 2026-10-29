package org.agmas.noellesroles.game.modes.echo.map.manor;

import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Kind;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;

/**
 * 200×200 地图的整数方块画布。
 *
 * <p>所有几何都只写整数坐标，不存在半格偏移，因此不会出现「方块错位」的缝隙：
 * 道路、广场、台地、假山都按整格覆盖，后画的图层直接盖掉先画的图层。
 *
 * <p>三类高度操作要分清：
 * <ul>
 * <li>{@link #column} / {@link #disc} / {@link #road} 把地面压回 {@code GROUND_Y}，
 * 并清空上方 {@link #CLEARANCE} 格，用来切穿台地修路；</li>
 * <li>{@link #raise} 抬高一段地面（台地），顶部方块保持整格；</li>
 * <li>{@link #rockery} 从基准面往上堆假山，每列都从地面连续填充，不会出现悬空石头。</li>
 * </ul>
 */
public final class EchoManorCanvas {
    private static final int WIDTH = EchoManorLayout.WIDTH;
    private static final int HEIGHT = EchoManorLayout.HEIGHT;
    private static final int DEPTH = EchoManorLayout.DEPTH;
    private static final int GROUND = EchoManorLayout.GROUND_Y;
    private static final int FEET = EchoManorLayout.FEET_Y;
    /** 压平地面时清空的高度：足够切穿最高的台地，又不会把整张图挖空。 */
    private static final int CLEARANCE = 9;

    private final byte[] cells = new byte[WIDTH * HEIGHT * DEPTH];

    public EchoManorCanvas() {
        java.util.Arrays.fill(cells, (byte) Kind.AIR.ordinal());
    }

    public byte[] snapshot() { return cells.clone(); }

    public Kind at(int x, int y, int z) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return Kind.AIR;
        return Kind.of(cells[Layout.index(x, y, z)] & 0xFF);
    }

    public boolean inside(int x, int z) {
        return x >= 0 && x < WIDTH && z >= 0 && z < DEPTH;
    }

    /** 严格写入：坐标越界说明布局算错了，直接报错而不是静默吞掉。 */
    public void put(int x, int y, int z, Kind kind) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) {
            throw new IllegalArgumentException("布局越界: " + x + "," + y + "," + z + " = " + kind);
        }
        cells[Layout.index(x, y, z)] = (byte) kind.ordinal();
    }

    private void putIfInside(int x, int y, int z, Kind kind) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return;
        cells[Layout.index(x, y, z)] = (byte) kind.ordinal();
    }

    public void fill(int x0, int x1, int y0, int y1, int z0, int z1, Kind kind) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) put(x, y, z, kind);
    }

    public void clear(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) put(x, y, z, Kind.AIR);
    }

    /** 单列压平：地表写 surface，上方清空。 */
    public void column(int x, int z, Kind surface) {
        if (!inside(x, z)) return;
        put(x, GROUND, z, surface);
        clear(x, x, FEET, FEET + CLEARANCE, z, z);
    }

    /** 整块压平（货场硬地、站台、街区地面）。 */
    public void flatten(int x0, int x1, int z0, int z1, Kind surface) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) column(x, z, surface);
    }

    /** 台地：从基准面上方抬升 height 格；顶部保持整格方块，侧面用挡土墙。 */
    public void raise(int x, int z, int height, Kind top, Kind side) {
        if (height <= 0) return;
        for (int y = FEET; y < GROUND + height; y++) putIfInside(x, y, z, side);
        putIfInside(x, GROUND + height, z, top);
        for (int y = GROUND + height + 1; y <= FEET + CLEARANCE; y++) putIfInside(x, y, z, Kind.AIR);
    }

    /** 圆盘铺地：会切穿台地，保证广场/路口和道路同高。 */
    public void disc(int cx, int cz, int radius, Kind surface) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                column(cx + dx, cz + dz, surface);
            }
        }
    }

    /** 圆环铺地（inner..outer 之间）。 */
    public void ring(int cx, int cz, int inner, int outer, Kind surface) {
        int in = inner * inner, out = outer * outer;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 < in || d2 > out) continue;
                column(cx + dx, cz + dz, surface);
            }
        }
    }

    /** 折线道路：每段按半格步进盖圆形印章，转角自然衔接、不会断缝。 */
    public void road(int[][] points, int radius, Kind surface) {
        for (int i = 0; i + 1 < points.length; i++) {
            stroke(points[i][0], points[i][1], points[i + 1][0], points[i + 1][1], radius, surface);
        }
    }

    private void stroke(double x0, double z0, double x1, double z1, int radius, Kind surface) {
        double length = Math.hypot(x1 - x0, z1 - z0);
        int steps = Math.max(1, (int) Math.ceil(length * 2.0D));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            disc((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(z0 + (z1 - z0) * t), radius, surface);
        }
    }

    /**
     * 假山：从基准面往上堆，越靠中心越高，边缘再叠一层确定性扰动。
     * 每列都从 y=GROUND+1 连续填到顶部，因此不会悬空、不会出现单格洞。
     */
    public void rockery(int cx, int cz, int radius, int peak, int seed) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                int x = cx + dx, z = cz + dz;
                if (!inside(x, z)) continue;
                double fall = 1.0D - Math.sqrt((double) d2) / (radius + 0.5D);
                int wobble = Math.floorMod(x * 37 + z * 91 + seed * 17, 7) < 3 ? 1 : 0;
                int height = (int) Math.round(peak * fall) + wobble;
                if (height <= 0) continue;
                int top = GROUND + height;
                for (int y = GROUND + 1; y < top; y++) {
                    Kind body = Math.floorMod(x * 13 + y * 29 + z * 7, 10) < 3 ? Kind.MOSS : Kind.ROCK;
                    putIfInside(x, y, z, body);
                }
                putIfInside(x, top, z, Math.floorMod(x + z, 3) == 0 ? Kind.MOSS : Kind.ROCK);
                for (int y = top + 1; y <= FEET + CLEARANCE; y++) putIfInside(x, y, z, Kind.AIR);
            }
        }
    }

    /** 该列最高的实心方块 y；没有则返回 -1。 */
    public int surfaceY(int x, int z) {
        for (int y = HEIGHT - 1; y >= 0; y--) if (at(x, y, z).solid()) return y;
        return -1;
    }

    /**
     * 树：只在自然草地/泥地上种，树干 + 三层树冠，全部整格方块。
     * 铺装、台地、假山、屋顶一律跳过，避免把景观种进建筑里。
     */
    public void tree(int x, int z, int trunk) {
        if (!inside(x, z)) return;
        Kind ground = at(x, GROUND, z);
        if (ground != Kind.GRASS && ground != Kind.DIRT) return;
        int base = surfaceY(x, z);
        if (base < GROUND || base + trunk + 2 >= HEIGHT) return;
        for (int y = base + 1; y <= base + trunk; y++) putIfInside(x, y, z, Kind.LOG);
        int crown = base + trunk;
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 0 ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    if (at(x + dx, crown + dy, z + dz) == Kind.AIR) {
                        putIfInside(x + dx, crown + dy, z + dz, Kind.LEAVES);
                    }
                }
            }
        }
        if (at(x, crown + 2, z) == Kind.AIR) putIfInside(x, crown + 2, z, Kind.LEAVES);
    }

    /** 路灯：只在铺装/草地上立杆，顶端一格灯；墙里与屋顶上不立。 */
    public void lamp(int x, int z, int height) {
        if (!inside(x, z)) return;
        Kind ground = at(x, GROUND, z);
        if (ground == Kind.AIR || ground == Kind.WALL || ground == Kind.ROOF || ground == Kind.ROOF_DARK) return;
        int base = surfaceY(x, z);
        if (base < 0 || base >= HEIGHT - height) return;
        for (int y = base + 1; y < base + height; y++) putIfInside(x, y, z, Kind.PILLAR);
        putIfInside(x, base + height, z, Kind.LAMP);
    }

    /** 集装箱：整箱覆盖，顶部加一层深色顶盖。 */
    public void container(int x0, int z0, int width, int depth, int height) {
        fill(x0, x0 + width - 1, FEET, FEET + height - 1, z0, z0 + depth - 1, Kind.CONTAINER);
        fill(x0, x0 + width - 1, FEET + height, FEET + height, z0, z0 + depth - 1, Kind.ROOF_DARK);
    }

    /** 简易房屋：内部先清空（防止被先画的假山/台地塞住），再四壁 + 两级屋顶。 */
    public void house(int x0, int x1, int z0, int z1, int wallTop, Kind floor) {
        fill(x0, x1, GROUND, GROUND, z0, z1, floor);
        clear(x0 + 1, x1 - 1, FEET, wallTop, z0 + 1, z1 - 1);
        fill(x0, x0, FEET, wallTop, z0, z1, Kind.WALL);
        fill(x1, x1, FEET, wallTop, z0, z1, Kind.WALL);
        fill(x0, x1, FEET, wallTop, z0, z0, Kind.WALL);
        fill(x0, x1, FEET, wallTop, z1, z1, Kind.WALL);
        fill(x0 - 2, x1 + 2, wallTop + 1, wallTop + 1, z0 - 2, z1 + 2, Kind.ROOF);
        fill(x0 - 1, x1 + 1, wallTop + 2, wallTop + 2, z0 - 1, z1 + 1, Kind.ROOF_DARK);
    }
}
