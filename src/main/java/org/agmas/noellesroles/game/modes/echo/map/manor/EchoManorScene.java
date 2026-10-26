package org.agmas.noellesroles.game.modes.echo.map.manor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineBlock;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.content.EchoHideBlock;
import org.agmas.noellesroles.game.modes.echo.content.EchoTerrainBlocks;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Kind;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;
import org.jetbrains.annotations.Nullable;

/**
 * 庄园场景的方块映射：把 {@link EchoManorLayout.Kind} 翻成实际 {@link BlockState}。
 *
 * <p>只使用整格方块，且刻意避开所有方块实体（箱子/熔炉/信标/讲台/床/旗帜…）：
 * 模板整块复制只搬方块状态，带方块实体的方块复制后容易留下空壳或残留旧 NBT，
 * 是「鬼畜冲突」的常见来源。这里也不需要邻居更新，所有装饰都是静止方块。
 *
 * <p>同一坐标的纹理变化由坐标哈希决定，因此每次生成结果完全一致，便于比对与复现。
 */
public final class EchoManorScene {
    private EchoManorScene() { }

    private static int hash(int x, int y, int z) {
        return Math.floorMod(x * 31 + y * 17 + z * 57, 100);
    }

    /** 仅供显式生成命令使用；根据布局点位区分核心信标和撤离面板，不迁移已有地图。 */
    public static BlockState state(Layout layout, int x, int y, int z) {
        var cell = layout.machineAt(x, y, z);
        if (cell != null) {
            return EchoMachines.block(machineKind(layout, x, y, z)).defaultBlockState()
                    .setValue(EchoMachineBlock.FACING, machineFacing(layout, x, y, z))
                    .setValue(EchoMachineBlock.PART, cell.part());
        }
        return sceneryState(layout.at(x, y, z), x, y, z);
    }

    /** 根点与所有部件共用同一种机器映射，不触发机器注册。 */
    @Nullable
    public static EchoMachines.Kind machineKind(Layout layout, int x, int y, int z) {
        var cell = layout.machineAt(x, y, z);
        return cell == null ? null : EchoMachines.Kind.valueOf(cell.machine().model().name());
    }

    /** 朝向来自占地规划，禁止按相邻空气重新选择而使部件旋转错位。 */
    public static Direction machineFacing(Layout layout, int x, int y, int z) {
        var cell = layout.machineAt(x, y, z);
        if (cell == null) return Direction.NORTH;
        return Direction.valueOf(cell.machine().facing().name());
    }

    /** 建筑方块保持原映射与完整碰撞，门体仍为铁块。 */
    private static BlockState sceneryState(Kind kind, int x, int y, int z) {
        int h = hash(x, y, z);
        return switch (kind) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case STONE -> (h < 70 ? Blocks.STONE : h < 85 ? Blocks.ANDESITE : Blocks.DEEPSLATE).defaultBlockState();
            case DIRT -> (h < 80 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState();
            case GRASS -> (h < 88 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK).defaultBlockState();
            case PATH -> (h < 78 ? Blocks.STONE_BRICKS : h < 90 ? Blocks.MOSSY_STONE_BRICKS
                    : Blocks.CHISELED_STONE_BRICKS).defaultBlockState();
            case FLOOR -> (x % 4 == 0 || z % 4 == 0 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS).defaultBlockState();
            case WALL -> (h < 85 ? Blocks.STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
            case GATE -> Blocks.IRON_BLOCK.defaultBlockState();
            case PILLAR -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case ROOF -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case ROOF_DARK -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case LAMP -> Blocks.SEA_LANTERN.defaultBlockState();
            case LOG -> Blocks.OAK_LOG.defaultBlockState();
            case LEAVES -> Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            case CONTAINER -> (h < 34 ? Blocks.RED_CONCRETE : h < 67 ? Blocks.BLUE_CONCRETE
                    : Blocks.ORANGE_CONCRETE).defaultBlockState();
            case TABLE -> Blocks.OAK_PLANKS.defaultBlockState();
            case COUNTER -> Blocks.SMOOTH_STONE.defaultBlockState();
            case BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
            case CRATE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case DARKSTONE -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            // 假山/台地/硬地：全部整格方块，无方块实体、无重力、无流体。
            case ROCK -> (h < 55 ? Blocks.STONE : h < 80 ? Blocks.ANDESITE : Blocks.TUFF).defaultBlockState();
            case MOSS -> (h < 60 ? Blocks.MOSS_BLOCK : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
            case CONCRETE -> (h < 70 ? Blocks.SMOOTH_STONE : h < 88 ? Blocks.POLISHED_ANDESITE
                    : Blocks.STONE_BRICKS).defaultBlockState();
            case PLATE -> (h < 80 ? Blocks.POLISHED_ANDESITE : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
            case TERRACE -> (h < 85 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
            case TERRACE_WALL -> (h < 75 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
            // 草丛：无碰撞、可穿行的隐身点，带朝向让模型不完全重复。
            case BUSH -> EchoTerrainBlocks.HIDE.defaultBlockState()
                    .setValue(EchoHideBlock.FACING, switch (Math.floorMod(x * 7 + z * 13, 4)) {
                        case 0 -> Direction.NORTH;
                        case 1 -> Direction.EAST;
                        case 2 -> Direction.SOUTH;
                        default -> Direction.WEST;
                    });
            // 矮墙：1 格高，挡视线不挡走位。
            case LOW_WALL -> (h < 60 ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL).defaultBlockState();
            case MARKER_CRYSTAL, MARKER_BEACON, MARKER_ALTAR, MARKER_CRATE, MARKER_BLACK_CRATE,
                    MARKER_BLACK_NODE, MARKER_BLACK_DROPOFF, MARKER_BROKER, MARKER_FREE_EXIT, PANEL, MACHINE_PART ->
                    throw new IllegalArgumentException("机器点位必须通过布局映射: " + kind);
        };
    }

    /** 生成时统一使用的写块标记：不触发邻居更新，避免流体/重力/红石级联。 */
    public static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
}
