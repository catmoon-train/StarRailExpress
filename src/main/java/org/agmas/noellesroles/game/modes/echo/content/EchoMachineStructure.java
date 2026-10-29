package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** 机器的格子契约；纯几何方法不触发方块注册，世界查询均不主动加载区块。 */
public final class EchoMachineStructure {
    public record Size(int width, int height, int depth) { }
    public record Cell(int x, int y, int z, int part) { }

    private static final Size LARGE = new Size(3, 2, 3);
    private static final Size SMALL = new Size(2, 2, 2);
    private static final List<Cell> LARGE_CELLS = buildCells(LARGE);
    private static final List<Cell> SMALL_CELLS = buildCells(SMALL);
    // 仅包围同步批量写入/拆除，不保存世界或机器实例，也不注册事件。
    private static final ThreadLocal<Integer> MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private EchoMachineStructure() { }

    public static Size size(EchoMachines.Kind kind) {
        return switch (kind) {
            case BEACON, CORE_BEACON, CRYSTAL, BLACK_NODE, ALTAR -> LARGE;
            default -> SMALL;
        };
    }

    public static List<Cell> cells(EchoMachines.Kind kind) {
        return size(kind).equals(LARGE) ? LARGE_CELLS : SMALL_CELLS;
    }

    private static List<Cell> buildCells(Size size) {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < size.height(); y++) {
            for (int z = 0; z < size.depth(); z++) {
                for (int x = 0; x < size.width(); x++) {
                    cells.add(new Cell(x, y, z, x + 3 * z + 9 * y));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** PART 使用固定三进制步长；小机器的合法编号不连续。 */
    public static BlockPos offset(int part, Direction facing) {
        if (part < 0 || part > 17) throw new IllegalArgumentException("part: " + part);
        int x = part % 3, y = part / 9, z = part / 3 % 3;
        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    /** 只做坐标运算，不表示锚点确实存在。非机器原样返回。 */
    public static BlockPos anchor(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EchoMachineBlock)) return pos;
        return pos.subtract(offset(state.getValue(EchoMachineBlock.PART), state.getValue(EchoMachineBlock.FACING)));
    }

    /** 目标与同种同朝向 PART=0 根均已加载且匹配时才解析到根。 */
    public static BlockPos root(LevelReader level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return pos;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EchoMachineBlock machine)
                || !validPart(machine.kind(), state.getValue(EchoMachineBlock.PART))) return pos;
        BlockPos root = anchor(pos, state);
        return loadedMatch(level, root, state, 0) ? root : pos;
    }

    public static AABB bounds(BlockPos root, EchoMachines.Kind kind, Direction facing) {
        Size size = size(kind);
        int last = size.width() - 1 + 3 * (size.depth() - 1) + 9 * (size.height() - 1);
        BlockPos opposite = root.offset(offset(last, facing));
        return new AABB(Math.min(root.getX(), opposite.getX()), Math.min(root.getY(), opposite.getY()),
                Math.min(root.getZ(), opposite.getZ()), Math.max(root.getX(), opposite.getX()) + 1,
                Math.max(root.getY(), opposite.getY()) + 1, Math.max(root.getZ(), opposite.getZ()) + 1);
    }

    public static boolean complete(LevelReader level, BlockPos root) {
        if (!level.hasChunkAt(root)) return false;
        BlockState state = level.getBlockState(root);
        if (!(state.getBlock() instanceof EchoMachineBlock machine) || state.getValue(EchoMachineBlock.PART) != 0) return false;
        for (Cell cell : cells(machine.kind())) {
            BlockPos pos = root.offset(offset(cell.part(), state.getValue(EchoMachineBlock.FACING)));
            if (!loadedMatch(level, pos, state, cell.part())) return false;
        }
        return true;
    }

    /** 无种类参数时采用完整 3 格宽索引域；实际方块镜像使用带 kind 的重载。 */
    public static int mirroredPart(int part, Mirror mirror) {
        return mirroredPart(part, mirror, LARGE);
    }

    public static int mirroredPart(int part, Mirror mirror, EchoMachines.Kind kind) {
        return mirroredPart(part, mirror, size(kind));
    }

    private static int mirroredPart(int part, Mirror mirror, Size size) {
        if (mirror == Mirror.NONE) return part;
        // 镜像同时反射 FACING 后，局部深度不变，局部左右必须交换。
        int x = part % 3;
        if (part < 0 || part > 17 || x >= size.width() || part / 3 % 3 >= size.depth()) return part;
        return part - x + size.width() - 1 - x;
    }

    static boolean validPart(EchoMachines.Kind kind, int part) {
        Size size = size(kind);
        return part >= 0 && part < 18 && part % 3 < size.width()
                && part / 3 % 3 < size.depth() && part / 9 < size.height();
    }

    static boolean loadedMatch(LevelReader level, BlockPos pos, BlockState sample, int part) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return false;
        BlockState actual = level.getBlockState(pos);
        return actual.is(sample.getBlock()) && actual.getValue(EchoMachineBlock.FACING) == sample.getValue(EchoMachineBlock.FACING)
                && actual.getValue(EchoMachineBlock.PART) == part;
    }

    static boolean loadedFootprint(LevelReader level, BlockPos root, BlockState state, EchoMachines.Kind kind) {
        for (Cell cell : cells(kind)) {
            BlockPos pos = root.offset(offset(cell.part(), state.getValue(EchoMachineBlock.FACING)));
            if (!level.hasChunkAt(pos)) return false;
        }
        return true;
    }

    static boolean mutating() { return MUTATION_DEPTH.get() != 0; }

    public static void mutate(Runnable action) {
        int depth = MUTATION_DEPTH.get();
        MUTATION_DEPTH.set(depth + 1);
        try {
            action.run();
        } finally {
            if (depth == 0) MUTATION_DEPTH.remove();
            else MUTATION_DEPTH.set(depth);
        }
    }

    /** 只删旧结构精确匹配的格子，保留被替换格及相邻机器；内部写入不产生额外掉落。 */
    static void remove(Level level, BlockPos changed, BlockState oldState, boolean preserveChanged) {
        EchoMachineBlock machine = (EchoMachineBlock) oldState.getBlock();
        BlockPos root = anchor(changed, oldState);
        List<BlockPos> removed = new ArrayList<>();
        mutate(() -> {
            List<Cell> owned = validPart(machine.kind(), oldState.getValue(EchoMachineBlock.PART))
                    ? cells(machine.kind()) : List.of();
            for (Cell cell : owned) {
                BlockPos pos = root.offset(offset(cell.part(), oldState.getValue(EchoMachineBlock.FACING)));
                if (preserveChanged && pos.equals(changed)) continue;
                if (loadedMatch(level, pos, oldState, cell.part())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                    removed.add(pos);
                }
            }
            if (!preserveChanged && level.hasChunkAt(changed) && level.getBlockState(changed) == oldState) {
                level.setBlock(changed, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                removed.add(changed);
            }
        });
        notifyNeighbors(level, removed, machine);
    }

    /** 不向未加载的相邻区块发送更新，也不在结构写到一半时传播邻居检查。 */
    static void notifyNeighbors(Level level, List<BlockPos> positions, Block block) {
        for (BlockPos pos : positions) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (level.hasChunkAt(neighbor) && !level.isOutsideBuildHeight(neighbor)) {
                    level.neighborChanged(neighbor, block, pos);
                }
            }
        }
    }
}
