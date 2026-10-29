package org.agmas.noellesroles.game.modes.echo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines.Kind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 只测索引、坐标与朝向变换；不初始化方块注册表，也不调用 Minecraft Bootstrap。 */
class EchoMachineStructureTest {
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Set<Kind> LARGE = Set.of(Kind.BEACON, Kind.CORE_BEACON, Kind.CRYSTAL, Kind.BLACK_NODE, Kind.ALTAR);

    @Test
    void sizesAndFixedStrideIndicesCoverExactlyThePhysicalVolume() {
        for (Kind kind : Kind.values()) {
            int width = LARGE.contains(kind) ? 3 : 2;
            assertEquals(new EchoMachineStructure.Size(width, 2, width), EchoMachineStructure.size(kind));
            var cells = EchoMachineStructure.cells(kind);
            assertEquals(width * 2 * width, cells.size());
            Set<Integer> parts = new HashSet<>();
            for (var cell : cells) {
                assertTrue(cell.x() >= 0 && cell.x() < width);
                assertTrue(cell.z() >= 0 && cell.z() < width);
                assertTrue(cell.y() >= 0 && cell.y() < 2);
                assertEquals(cell.x() + 3 * cell.z() + 9 * cell.y(), cell.part());
                assertTrue(parts.add(cell.part()));
                assertTrue(EchoMachineStructure.validPart(kind, cell.part()));
            }
            assertThrows(UnsupportedOperationException.class, () -> cells.clear());
            for (int part = 0; part < 18; part++) {
                assertEquals(parts.contains(part), EchoMachineStructure.validPart(kind, part));
            }
            assertFalse(EchoMachineStructure.validPart(kind, -1));
            assertFalse(EchoMachineStructure.validPart(kind, 18));
            if (width == 2) assertEquals(Set.of(0, 1, 3, 4, 9, 10, 12, 13), parts);
        }
    }

    @Test
    void directionOffsetsMatchTheBlockstateQuarterTurns() {
        assertEquals(new BlockPos(2, 1, 1), EchoMachineStructure.offset(14, Direction.NORTH));
        assertEquals(new BlockPos(-1, 1, 2), EchoMachineStructure.offset(14, Direction.EAST));
        assertEquals(new BlockPos(-2, 1, -1), EchoMachineStructure.offset(14, Direction.SOUTH));
        assertEquals(new BlockPos(1, 1, -2), EchoMachineStructure.offset(14, Direction.WEST));
        assertThrows(IllegalArgumentException.class, () -> EchoMachineStructure.offset(18, Direction.NORTH));
        assertThrows(IllegalArgumentException.class, () -> EchoMachineStructure.offset(0, Direction.UP));
        for (Kind kind : Kind.values()) {
            for (var cell : EchoMachineStructure.cells(kind)) {
                for (Direction facing : FACINGS) {
                    for (Rotation rotation : Rotation.values()) {
                        assertEquals(EchoMachineStructure.offset(cell.part(), facing).rotate(rotation),
                                EchoMachineStructure.offset(cell.part(), rotation.rotate(facing)));
                    }
                }
            }
        }
    }

    @Test
    void everyCellResolvesToTheSameRootByPureSubtraction() {
        BlockPos root = new BlockPos(-31, 65, 15);
        for (Kind kind : Kind.values()) {
            for (Direction facing : FACINGS) {
                Set<BlockPos> positions = new HashSet<>();
                for (var cell : EchoMachineStructure.cells(kind)) {
                    BlockPos offset = EchoMachineStructure.offset(cell.part(), facing);
                    BlockPos pos = root.offset(offset);
                    assertTrue(positions.add(pos));
                    assertEquals(root, pos.subtract(offset));
                }
            }
        }
    }

    @Test
    void boundsEncloseWholeBlocksIncludingNegativeRotatedCoordinates() {
        BlockPos root = new BlockPos(-31, 65, 15);
        for (Kind kind : Kind.values()) {
            for (Direction facing : FACINGS) {
                AABB expected = null;
                for (var cell : EchoMachineStructure.cells(kind)) {
                    AABB box = new AABB(root.offset(EchoMachineStructure.offset(cell.part(), facing)));
                    expected = expected == null ? box : expected.minmax(box);
                }
                AABB actual = EchoMachineStructure.bounds(root, kind, facing);
                assertEquals(expected, actual);
                assertEquals(EchoMachineStructure.cells(kind).size(), actual.getXsize() * actual.getYsize() * actual.getZsize());
            }
        }
    }

    @Test
    void mirroredPartsAreSizeAwareInvolutionsAndRestoreOneSharedRoot() {
        BlockPos originalRoot = new BlockPos(19, 64, -17);
        for (Kind kind : Kind.values()) {
            for (Direction facing : FACINGS) {
                for (Mirror mirror : Mirror.values()) {
                    BlockPos commonRoot = null;
                    Set<Integer> mirroredParts = new HashSet<>();
                    for (var cell : EchoMachineStructure.cells(kind)) {
                        int part = EchoMachineStructure.mirroredPart(cell.part(), mirror, kind);
                        assertTrue(EchoMachineStructure.validPart(kind, part));
                        assertTrue(mirroredParts.add(part));
                        assertEquals(cell.part(), EchoMachineStructure.mirroredPart(part, mirror, kind));
                        BlockPos pos = originalRoot.offset(EchoMachineStructure.offset(cell.part(), facing));
                        BlockPos transformed = mirror(pos, mirror);
                        BlockPos recovered = transformed.subtract(EchoMachineStructure.offset(part, mirror.mirror(facing)));
                        if (commonRoot == null) commonRoot = recovered;
                        else assertEquals(commonRoot, recovered, kind + "/" + facing + "/" + mirror);
                    }
                    assertEquals(EchoMachineStructure.cells(kind).size(), mirroredParts.size());
                }
            }
        }
        assertEquals(2, EchoMachineStructure.mirroredPart(0, Mirror.LEFT_RIGHT));
        assertEquals(1, EchoMachineStructure.mirroredPart(0, Mirror.LEFT_RIGHT, Kind.SUPPLY));
        assertEquals(0, EchoMachineStructure.mirroredPart(0, Mirror.NONE, Kind.SUPPLY));
    }

    @Test
    void neighboringSameKindMachinesCannotShareAnOwnershipTuple() {
        for (Kind kind : Kind.values()) {
            for (Direction facing : FACINGS) {
                BlockPos root = new BlockPos(15, 64, 15);
                var volume = EchoMachineStructure.bounds(root, kind, facing);
                BlockPos neighborRoot = root.offset((int) volume.getXsize(), 0, 0);
                Set<BlockPos> occupied = EchoMachineStructure.cells(kind).stream()
                        .map(cell -> root.offset(EchoMachineStructure.offset(cell.part(), facing))).collect(Collectors.toSet());
                for (var cell : EchoMachineStructure.cells(kind)) {
                    BlockPos neighborPos = neighborRoot.offset(EchoMachineStructure.offset(cell.part(), facing));
                    assertFalse(occupied.contains(neighborPos));
                    assertNotEquals(root, neighborPos.subtract(EchoMachineStructure.offset(cell.part(), facing)));
                }
            }
        }
    }

    @Test
    void isolatedLanguageFilesHaveMatchingPlacementAndTooltipKeys() throws Exception {
        Pattern key = Pattern.compile("\"(echo_multiblock\\.[^\"]+)\"\\s*:");
        Set<String> expected = Set.of("echo_multiblock.tooltip.size", "echo_multiblock.tooltip.anchor",
                "echo_multiblock.place.bounds", "echo_multiblock.place.unloaded", "echo_multiblock.place.occupied",
                "echo_multiblock.place.permission", "echo_multiblock.place.collision", "echo_multiblock.place.changed");
        for (String locale : List.of("zh_cn", "zh_tw", "en_us")) {
            String text = Files.readString(Path.of("src/main/resources/assets/echo_multiblock/lang/" + locale + ".json"));
            assertEquals(expected, key.matcher(text).results().map(match -> match.group(1)).collect(Collectors.toSet()));
        }
    }

    private static BlockPos mirror(BlockPos pos, Mirror mirror) {
        return switch (mirror) {
            case NONE -> pos;
            case LEFT_RIGHT -> new BlockPos(pos.getX(), pos.getY(), -pos.getZ());
            case FRONT_BACK -> new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
        };
    }
}
