package org.agmas.noellesroles.client;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayDeque;

/**
 * 箱庭视野（{@link ModEffects#HAKONIWA_VISION}）客户端核心，移植自 dungeons-perspective
 * （{@code FloodCuller} + {@code GenericCuller3}）。两层剔除叠加：
 *
 * <p><b>一、逐列天花板（局部屋顶开孔）</b>：以玩家头部为种子对连通空气做三维泛洪，记录每个 XZ 列
 * 泛洪到的最高空气高度，高于该列天花板的方块一律剔除。注意泛洪只从距玩家水平
 * {@value #FLOOD_RADIUS} 格以内的节点继续扩散 —— 它挖的是玩家头顶的一个小孔，而不是整间房的屋顶。
 * 半径小是关键：泛洪必然跑完，不会被访问上限截断；一旦截断，边缘列只访问到低处空气，
 * 天花板会被记成极低值，它上方的一切都会被误剔。
 *
 * <p><b>二、视线圆锥（遮挡剔除）</b>：这才是主力。沿「玩家 → 相机」轴剔除轴心附近、位于玩家脚下
 * 平面之上、且比相机更近的方块；近端是半角 45° 的圆锥（贴着玩家不会剔太多），远端收敛为半径
 * {@value #SIGHT_RADIUS} 的圆柱，另外贴近相机 {@value #CAMERA_NEAR_CULL_RADIUS} 格内的一律剔除。
 * 俯视时它是玩家头顶的一根竖直管道，侧视 / 2.5D 时它挖穿镜头与玩家之间的近墙。门、梯子、
 * 按钮拉杆等贴面方块不参与该层剔除（同参考实现），否则功能方块会凭空消失。
 *
 * <p><b>剔除通道</b>：方块在区块网格构建期被替换为空气（vanilla：RenderChunkRegion；
 * sodium：LevelSlice，见对应 mixin）；实体由 EntityRenderDispatcher 的 shouldRender 剔除；
 * 指针射线经 {@link HakoniwaClipContext} 把被剔除的方块视为空体素，从而不会打到已隐藏的二楼。
 * 前两者在渲染线程之外调用，故剔除状态以不可变快照 + volatile 引用发布。
 */
public final class HakoniwaVisionClientHandle {

    private static final int SCAN_INTERVAL_TICKS = 10;
    /**
     * 泛洪继续扩散的水平半径（格）。对应参考实现的 {@code maxDist = 16 * cullAngle / 30}
     * （默认 cullAngle = 6，即 3.2）。放大它会让泛洪有被截断的风险，进而产生错误的天花板高度。
     */
    private static final double FLOOD_RADIUS = 3.2D;
    private static final int FLOOD_UP = 24;
    private static final int FLOOD_DOWN = 8;
    /**
     * 天花板之上被剔除的最大厚度。俯视镜头高度为 {@code max(8, 相机距离 + 6)}，而相机距离上限 64
     * （见 {@code ModEffects#getTwoDimensionalCameraDistance}），故 72 必然越过镜头；
     * 有了上界才能算出需要标脏的 section 范围。
     */
    private static final int ROOF_CULL_HEIGHT = 72;
    /** 视线圆柱的远端半径（格）；对应参考实现的 {@code cullAngle}。 */
    private static final double SIGHT_RADIUS = 10.0D;
    /** 视线圆锥的半角正切；1.0 = 45°。 */
    private static final double SIGHT_CONE_TAN = 1.0D;
    /** 距相机这么近的方块一律剔除，避免镜头埋进方块里。 */
    private static final double CAMERA_NEAR_CULL_RADIUS = 5.0D;
    /** 普通视角下触发剔除的俯角阈值。 */
    private static final float TOP_PITCH_THRESHOLD = 45.0F;
    /**
     * 高出本地玩家不超过这么多格的实体一律不剔除。视线圆锥近端是 45° 的锥面，俯视时轴竖直向上，
     * 头顶一两格、水平一两格内的东西必然落进锥内 —— 楼梯 / 台阶上只高一级的玩家会被当成遮挡物剔掉。
     * 遮挡玩家的是方块，不是与他几乎同高的其他玩家。
     */
    private static final double ENTITY_CULL_MIN_HEIGHT = 2.0D;
    /** {@link Long2IntOpenHashMap} 的缺省值：该 XZ 列不在泛洪范围内。 */
    private static final int NO_CEILING = Integer.MIN_VALUE;
    /** {@code Direction.values()} 每次调用都克隆数组，而泛洪要逐格遍历它。 */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** 当前生效的剔除快照；null 表示未激活。发布后绝不修改，故可被网格构建线程无锁读取。 */
    private static volatile Cull current;
    private static BlockPos lastScanOrigin = BlockPos.ZERO;
    private static long lastScanTick = Long.MIN_VALUE;

    private HakoniwaVisionClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HakoniwaVisionClientHandle::tick);
    }

    /**
     * 方块剔除判定：在区块网格构建的工作线程上被逐方块调用，也用于指针射线，必须极快。
     * {@code state} 为 null 时跳过「贴面方块不剔除」的类型豁免。
     */
    public static boolean shouldHideBlock(BlockState state, int x, int y, int z) {
        Cull cull = current;
        return cull != null && cull.hides(state, x, y, z);
    }

    /** 实体剔除判定：落在被剔除区域内的实体（屋顶上的、近墙里的）不渲染，指针也不应命中。 */
    public static boolean shouldCullEntity(Entity entity) {
        Cull cull = current;
        if (cull == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player || entity == client.getCameraEntity()) {
            return false;
        }
        LocalPlayer self = client.player;
        if (self != null && entity.getY() - self.getY() <= ENTITY_CULL_MIN_HEIGHT) {
            return false; // 与本地玩家几乎同高（含低于他）：楼梯上下相邻一级也要互相可见
        }
        return cull.hides(null, entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
    }

    public static boolean isActive() {
        return current != null;
    }

    // ==================== Tick ====================

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            deactivate();
            return;
        }
        // 二维视角必然需要遮挡剔除（否则屋顶 / 近墙挡住玩家自己），故它一并启用本剔除，
        // 不要求额外授予 HAKONIWA_VISION —— 逐块剔除是二维视角遮挡剔除的唯一手段。
        boolean twoDimensional = TwoDimensionalCameraClientHandle.isActive();
        if (!twoDimensional && !player.hasEffect(ModEffects.HAKONIWA_VISION)) {
            deactivate();
            return;
        }
        if (!twoDimensional && player.getXRot() <= TOP_PITCH_THRESHOLD) {
            deactivate();
            return;
        }
        Vec3 cameraPos = cameraPosition(client);
        if (cameraPos == null) {
            deactivate();
            return;
        }

        long gameTime = client.level.getGameTime();
        BlockPos origin = player.blockPosition();
        boolean due = lastScanTick == Long.MIN_VALUE
                || !origin.equals(lastScanOrigin)
                || gameTime - lastScanTick >= SCAN_INTERVAL_TICKS;
        if (!due) {
            return;
        }
        lastScanTick = gameTime;
        lastScanOrigin = origin;

        Cull next = Cull.of(floodCeilings(client.level, player), player.position(), cameraPos, client.level);
        // 定期重扫时玩家往往原地未动：快照等价就别去标脏那几十个 section。
        if (next.equivalentTo(current)) {
            return;
        }
        publish(next);
    }

    /** 二维视角下用它自己的固定镜头；否则用主相机（普通视角俯视时的第三人称镜头）。 */
    private static Vec3 cameraPosition(Minecraft client) {
        Vec3 twoDimensional = TwoDimensionalCameraClientHandle.cameraPosition();
        if (twoDimensional != null) {
            return twoDimensional;
        }
        Camera camera = client.gameRenderer.getMainCamera();
        return camera.isInitialized() ? camera.getPosition() : null;
    }

    private static void deactivate() {
        publish(null);
        lastScanTick = Long.MIN_VALUE;
    }

    /** 切换快照，并把新旧快照覆盖的区域标脏以触发网格重建。 */
    private static void publish(Cull next) {
        Cull previous = current;
        if (previous == null && next == null) {
            return;
        }
        current = next;
        if (previous != null) {
            markSectionsDirty(previous);
        }
        if (next != null) {
            markSectionsDirty(next);
        }
    }

    private static void markSectionsDirty(Cull cull) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) {
            return;
        }
        int sMinX = SectionPos.blockToSectionCoord(cull.minX);
        int sMinY = SectionPos.blockToSectionCoord(cull.minY);
        int sMinZ = SectionPos.blockToSectionCoord(cull.minZ);
        int sMaxX = SectionPos.blockToSectionCoord(cull.maxX);
        int sMaxY = SectionPos.blockToSectionCoord(cull.maxY);
        int sMaxZ = SectionPos.blockToSectionCoord(cull.maxZ);
        for (int sx = sMinX; sx <= sMaxX; sx++) {
            for (int sy = sMinY; sy <= sMaxY; sy++) {
                for (int sz = sMinZ; sz <= sMaxZ; sz++) {
                    client.levelRenderer.setSectionDirtyWithNeighbors(sx, sy, sz);
                }
            }
        }
    }

    // ==================== 逐列天花板泛洪 ====================

    /**
     * 从玩家头部的连通空气出发做三维泛洪，返回「XZ 列 → 该列泛洪到的最高空气 Y」。
     * 节点一律记录天花板，但只有落在 {@value #FLOOD_RADIUS} 格半径内的节点才继续向外扩散，
     * 因此结果被限制在玩家头顶的一小片区域，且必然跑完（不存在被上限截断的半成品天花板图）。
     */
    private static Long2IntOpenHashMap floodCeilings(Level level, LocalPlayer player) {
        Long2IntOpenHashMap ceilings = new Long2IntOpenHashMap();
        ceilings.defaultReturnValue(NO_CEILING);

        BlockPos seed = player.blockPosition().above();
        if (!isPassable(level, seed)) {
            seed = player.blockPosition();
            if (!isPassable(level, seed)) {
                return ceilings;
            }
        }

        int minY = seed.getY() - FLOOD_DOWN;
        int maxY = seed.getY() + FLOOD_UP;
        int seedX = seed.getX();
        int seedZ = seed.getZ();
        double radiusSqr = FLOOD_RADIUS * FLOOD_RADIUS;

        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> stack = new ArrayDeque<>();
        stack.push(seed);
        visited.add(seed.asLong());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!stack.isEmpty()) {
            BlockPos pos = stack.pop();
            long column = packXZ(pos.getX(), pos.getZ());
            if (pos.getY() > ceilings.get(column)) {
                ceilings.put(column, pos.getY());
            }

            double dx = pos.getX() - seedX;
            double dz = pos.getZ() - seedZ;
            if (dx * dx + dz * dz > radiusSqr) {
                continue; // 已在半径之外：记录它的天花板，但不再向外扩散
            }
            for (Direction direction : DIRECTIONS) {
                cursor.setWithOffset(pos, direction);
                if (cursor.getY() > maxY || cursor.getY() < minY) {
                    continue;
                }
                if (!visited.add(cursor.asLong())) {
                    continue;
                }
                if (isPassable(level, cursor)) {
                    stack.push(cursor.immutable());
                }
            }
        }
        return ceilings;
    }

    /** 泛洪可通过性：空气 / 无碰撞方块可通过；墙体、玻璃、关闭的门阻挡。 */
    private static boolean isPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    /** 门 / 梯子 / 按钮拉杆等贴面功能方块不参与视线圆锥剔除，否则它们会凭空消失。 */
    private static boolean isIgnoredType(BlockState state) {
        var block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof LadderBlock
                || block instanceof FaceAttachedHorizontalDirectionalBlock
                || block instanceof SpawnerBlock
                || block instanceof TrialSpawnerBlock
                || block instanceof VaultBlock;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ==================== 剔除快照 ====================

    /** 不可变剔除快照。{@code minX..maxX} 等为它影响到的方块范围，仅用于标脏与热路径早退。 */
    private static final class Cull {
        private final Long2IntOpenHashMap ceilings;
        private final double originX, originY, originZ;
        private final double cameraX, cameraY, cameraZ;
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        private Cull(Long2IntOpenHashMap ceilings,
                     double originX, double originY, double originZ,
                     double cameraX, double cameraY, double cameraZ,
                     int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.ceilings = ceilings;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static Cull of(Long2IntOpenHashMap ceilings, Vec3 origin, Vec3 camera, Level level) {
            // 视线圆锥覆盖「玩家 → 相机」线段外扩一个圆柱半径。
            int pad = Mth.ceil(SIGHT_RADIUS) + 1;
            int minX = Mth.floor(Math.min(origin.x, camera.x)) - pad;
            int maxX = Mth.ceil(Math.max(origin.x, camera.x)) + pad;
            int minY = Mth.floor(Math.min(origin.y, camera.y)) - pad;
            int maxY = Mth.ceil(Math.max(origin.y, camera.y)) + pad;
            int minZ = Mth.floor(Math.min(origin.z, camera.z)) - pad;
            int maxZ = Mth.ceil(Math.max(origin.z, camera.z)) + pad;

            for (var entry : ceilings.long2IntEntrySet()) {
                int x = (int) (entry.getLongKey() >> 32);
                int z = (int) entry.getLongKey();
                int ceiling = entry.getIntValue();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                minY = Math.min(minY, ceiling + 1);
                maxY = Math.max(maxY, ceiling + ROOF_CULL_HEIGHT);
            }

            minY = Math.max(minY, level.getMinBuildHeight());
            maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
            return new Cull(ceilings, origin.x, origin.y, origin.z, camera.x, camera.y, camera.z,
                    minX, minY, minZ, maxX, maxY, maxZ);
        }

        /** 圆锥几何（玩家 / 相机位置）与逐列天花板都一致时，两个快照剔除的方块集合完全相同。 */
        boolean equivalentTo(Cull other) {
            return other != null
                    && other.originX == this.originX && other.originY == this.originY
                    && other.originZ == this.originZ && other.cameraX == this.cameraX
                    && other.cameraY == this.cameraY && other.cameraZ == this.cameraZ
                    && other.ceilings.equals(this.ceilings);
        }

        boolean hides(BlockState state, int x, int y, int z) {
            // 包围盒早退：本方法在网格构建线程上被逐方块调用，绝大多数方块落在剔除区域之外。
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                return false;
            }
            if (aboveCeiling(x, y, z)) {
                return true;
            }
            if (state != null && isIgnoredType(state)) {
                return false;
            }
            return insideSightCone(x + 0.5D, y + 0.5D, z + 0.5D);
        }

        private boolean aboveCeiling(int x, int y, int z) {
            int ceiling = ceilings.get(packXZ(x, z));
            return ceiling != NO_CEILING && y > ceiling && y - ceiling <= ROOF_CULL_HEIGHT;
        }

        /** 「玩家 → 相机」轴上的圆锥（近端）/ 圆柱（远端）。轴向背后、相机之外、玩家脚下的方块都不剔。 */
        private boolean insideSightCone(double blockX, double blockY, double blockZ) {
            // 参考实现取 originY + 1（其等距镜头始终在高处）。这里放宽到脚下平面，
            // 否则纯侧视（amplifier 5~8，镜头与玩家等高）会在玩家腿前留下一截齐膝的墙。
            if (blockY <= originY) {
                return false;
            }

            double axisX = cameraX - originX;
            double axisY = cameraY - originY;
            double axisZ = cameraZ - originZ;
            double axisLenSqr = axisX * axisX + axisY * axisY + axisZ * axisZ;
            if (axisLenSqr <= 1.0E-6D) {
                return false;
            }

            double toX = blockX - originX;
            double toY = blockY - originY;
            double toZ = blockZ - originZ;
            if (toX * toX + toY * toY + toZ * toZ >= axisLenSqr) {
                return false; // 比相机还远，挡不住玩家
            }

            double camX = cameraX - blockX;
            double camY = cameraY - blockY;
            double camZ = cameraZ - blockZ;
            if (camX * camX + camY * camY + camZ * camZ < CAMERA_NEAR_CULL_RADIUS * CAMERA_NEAR_CULL_RADIUS) {
                return true; // 紧贴镜头
            }

            double dot = toX * axisX + toY * axisY + toZ * axisZ;
            if (dot <= 0.0D) {
                return false; // 在玩家背朝相机的一侧
            }

            double crossX = toY * axisZ - toZ * axisY;
            double crossY = toZ * axisX - toX * axisZ;
            double crossZ = toX * axisY - toY * axisX;
            double perpDistSqr = (crossX * crossX + crossY * crossY + crossZ * crossZ) / axisLenSqr;
            double projDist = dot / Math.sqrt(axisLenSqr);
            double radius = Math.min(projDist * SIGHT_CONE_TAN, SIGHT_RADIUS);
            return perpDistSqr <= radius * radius;
        }
    }
}
