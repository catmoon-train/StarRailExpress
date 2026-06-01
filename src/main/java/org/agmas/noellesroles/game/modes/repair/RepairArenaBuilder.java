package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.game.data.MapConfig;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RepairArenaBuilder {
    private static final Map<ServerLevel, ArenaState> ARENAS = new java.util.WeakHashMap<>();
    private static final BlockPos DEFAULT_MANSION_BASE = new BlockPos(-8022, 80, -8028);
    private static final BlockPos DEFAULT_SELECTION_BASE = new BlockPos(-8000, 96, -8112);

    private RepairArenaBuilder() {
    }

    public static void prepare(ServerLevel level, List<ServerPlayer> players) {
        restoreAll(level);
        ArenaState state = new ArenaState();
        ARENAS.put(level, state);
        buildSelectionRoom(level, state, players);
        MapConfig.RepairConfig repairConfig = RepairMapRuntimeConfig.current(level).orElse(null);
        if (repairConfig != null) {
            buildConfiguredGameplay(level, state, repairConfig);
        } else {
            buildDefaultMansionTemplate(level, state);
        }
        RepairLootSpawner.prepare(level, repairConfig);
        RepairLockedDoorState.prepare(level, repairConfig);
        placePlayers(level, state, players);
    }

    public static void tickSelection(ServerLevel level) {
        ArenaState state = ARENAS.get(level);
        if (state == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            PlayerSlot slot = state.playerSlots.get(player.getUUID());
            if (slot == null) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 10, false, false, true));
            if (!player.isPassenger()) {
                player.teleportTo(level, slot.x, slot.y, slot.z, slot.yaw, slot.pitch);
            }
        }
    }

    public static void finishSelection(ServerLevel level) {
        ArenaState state = ARENAS.get(level);
        if (state == null || state.selectionRestored) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            PlayerSlot slot = state.playerSlots.get(player.getUUID());
            if (slot == null) {
                continue;
            }
            player.stopRiding();
            player.teleportTo(level, slot.originalX, slot.originalY, slot.originalZ, slot.originalYaw, slot.originalPitch);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        for (ArmorStand stand : state.seats) {
            stand.discard();
        }
        restoreBlocks(level, state.selectionBlocks);
        state.selectionRestored = true;
    }

    public static void restoreAll(ServerLevel level) {
        ArenaState state = ARENAS.remove(level);
        if (state == null) {
            return;
        }
        finishSelection(level, state);
        restoreBlocks(level, state.gameplayBlocks);
        RepairLootSpawner.reset(level);
        RepairLockedDoorState.reset(level);
    }

    public static void trackGameplayPlacement(ServerLevel level, BlockPos pos) {
        ArenaState state = ARENAS.get(level);
        if (state != null) {
            snapshot(level, state.gameplayBlocks, pos);
        }
    }

    public static boolean isReady(ServerLevel level) {
        return ARENAS.containsKey(level);
    }

    public static void teleportToDefaultGameplaySpawn(ServerPlayer player, boolean hunter, int index) {
        ServerLevel level = player.serverLevel();
        BlockPos base = defaultMansionBase(level);
        int[][] hunterSpawns = {
                { 22, 1, 28 }, { 20, 1, 32 }, { 24, 1, 32 }, { 22, 1, 36 }
        };
        int[][] survivorSpawns = {
                { 18, 1, 24 }, { 22, 1, 24 }, { 26, 1, 24 }, { 18, 1, 34 },
                { 22, 1, 34 }, { 26, 1, 34 }, { 20, 1, 40 }, { 24, 1, 40 }
        };
        int[] offset = hunter
                ? hunterSpawns[Math.floorMod(index, hunterSpawns.length)]
                : survivorSpawns[Math.floorMod(index, survivorSpawns.length)];
        BlockPos pos = base.offset(offset[0], offset[1], offset[2]);
        float yaw = hunter ? 180.0F : 0.0F;
        player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
    }

    private static void finishSelection(ServerLevel level, ArenaState state) {
        if (state.selectionRestored) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            PlayerSlot slot = state.playerSlots.get(player.getUUID());
            if (slot != null) {
                player.stopRiding();
                player.teleportTo(level, slot.originalX, slot.originalY, slot.originalZ, slot.originalYaw, slot.originalPitch);
            }
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        for (ArmorStand stand : state.seats) {
            stand.discard();
        }
        restoreBlocks(level, state.selectionBlocks);
        state.selectionRestored = true;
    }

    private static void buildSelectionRoom(ServerLevel level, ArenaState state, List<ServerPlayer> players) {
        BlockPos base = DEFAULT_SELECTION_BASE;
        state.selectionCenter = base;

        for (int x = -10; x <= 10; x++) {
            for (int z = -7; z <= 11; z++) {
                placeSelection(level, state, base.offset(x, 0, z), checker(x, z));
                for (int y = 1; y <= 5; y++) {
                    if (x == -10 || x == 10 || z == -7 || z == 11) {
                        placeSelection(level, state, base.offset(x, y, z), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    } else if (y <= 4) {
                        placeSelection(level, state, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
                if (x > -10 && x < 10 && z > -7 && z < 11) {
                    placeSelection(level, state, base.offset(x, 6, z), Blocks.SPRUCE_SLAB.defaultBlockState());
                }
            }
        }

        for (int i = -1; i <= 1; i++) {
            placeSelection(level, state, base.offset(i * 5, 4, -5),
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            placeSelection(level, state, base.offset(i * 6, 1, 9), Blocks.BOOKSHELF.defaultBlockState());
        }


    }

    private static BlockState checker(int x, int z) {
        return ((x + z) & 1) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static void placePlayers(ServerLevel level, ArenaState state, List<ServerPlayer> players) {
        BlockPos center = state.selectionCenter;
        int count = Math.max(1, players.size());
        double radius = 5.3D;
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            double t = count == 1 ? 0.5D : i / (double) (count - 1);
            double angle = Math.toRadians(210.0D - t * 240.0D);
            double x = center.getX() + 0.5D + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5D + Math.sin(angle) * radius;
            double y = center.getY() + 1.05D;
            float yaw = (float) (Math.toDegrees(Math.atan2(center.getZ() + 0.5D - z, center.getX() + 0.5D - x)) - 90.0D);
            PlayerSlot slot = new PlayerSlot(player.getX(), player.getY(), player.getZ(), player.getYRot(),
                    player.getXRot(), x, y, z, yaw, 0.0F);
            state.playerSlots.put(player.getUUID(), slot);
            player.teleportTo(level, x, y, z, yaw, 0.0F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 10, false, false, true));
            if (i % 4 == 1) {
                ArmorStand seat = EntityType.ARMOR_STAND.create(level);
                if (seat != null) {
                    seat.moveTo(x, y - 1.0D, z, yaw, 0.0F);
                    seat.setInvisible(true);
                    seat.setNoGravity(true);
                    seat.setSmall(true);
                    seat.setInvulnerable(true);
                    level.addFreshEntity(seat);
                    player.startRiding(seat, true);
                    state.seats.add(seat);
                }
            }
        }
    }

    static BlockPos defaultMansionBase(ServerLevel level) {
        return DEFAULT_MANSION_BASE;
    }

    private static void buildDefaultMansionTemplate(ServerLevel level, ArenaState state) {
        BlockPos base = defaultMansionBase(level);

        clearGameplayVolume(level, state, base.offset(-6, 0, -6), base.offset(50, 11, 62));

        // Region-based mansion footprint: foyer/courtyard south, kitchen/storage and study middle,
        // bedroom/infirmary and workshop/power north, plus basement boiler and attic storage landmarks.
        for (int x = 0; x <= 44; x++) {
            for (int z = 0; z <= 56; z++) {
                placeGameplay(level, state, base.offset(x, 0, z), checker(x, z));
                for (int y = 1; y <= 5; y++) {
                    boolean outer = x == 0 || x == 44 || z == 0 || z == 56;
                    boolean verticalWall = (x == 14 || x == 28) && z > 0 && z < 56;
                    boolean horizontalWall = (z == 14 || z == 30 || z == 44) && x > 0 && x < 44;
                    placeGameplay(level, state, base.offset(x, y, z),
                            outer || verticalWall || horizontalWall
                                    ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
                }
                placeGameplay(level, state, base.offset(x, 6, z), Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        }

        // External routes: main gate, secret tunnel, repair lift, boiler pipe, and courtyard branch gaps.
        clearPassage(level, state, base.offset(21, 1, 56), true, 5);
        clearPassage(level, state, base.offset(0, 1, 22), false, 3);
        clearPassage(level, state, base.offset(44, 1, 22), false, 3);
        clearPassage(level, state, base.offset(21, 1, 0), true, 3);

        // Doorways are always carved through a real wall segment and leave wall blocks on both sides.
        doorway(level, state, base, 14, 8, false, false);  // 改为木门
        doorway(level, state, base, 28, 8, false, false);  // 改为木门
        doorway(level, state, base, 14, 22, false, false); // 改为木门
        doorway(level, state, base, 28, 22, false, false); // 改为木门
        doorway(level, state, base, 14, 38, false, true);  // 保留1个铁门
        doorway(level, state, base, 28, 38, false, false); // 改为木门
        doorway(level, state, base, 10, 14, true, false);
        doorway(level, state, base, 22, 14, true, false);
        doorway(level, state, base, 34, 14, true, false);
        doorway(level, state, base, 10, 30, true, false);
        doorway(level, state, base, 22, 30, true, false);
        doorway(level, state, base, 34, 30, true, false);
        doorway(level, state, base, 10, 44, true, false);
        doorway(level, state, base, 22, 44, true, false); // 改为木门
        doorway(level, state, base, 34, 44, true, false); // 改为木门

        // Break the grid feeling with readable room features and a few chase loops.
        placeRoomDetails(level, state, base);

        int[][] stations = { { 7, 8 }, { 36, 8 }, { 7, 36 }, { 36, 36 }, { 22, 49 } };
        for (int[] offset : stations) {
            BlockPos pos = base.offset(offset[0], 1, offset[1]);
            placeGameplay(level, state, pos, ModBlocks.REPAIR_STATION.defaultBlockState());
            placeGameplay(level, state, pos.above(), Blocks.CHAIN.defaultBlockState());
        }

        int[][] cages = { { 22, 8 }, { 6, 49 }, { 38, 49 }, { 22, 24 } };
        for (int[] offset : cages) {
            BlockPos pos = base.offset(offset[0], 1, offset[1]);
            placeGameplay(level, state, pos, ModBlocks.HUNTER_CAGE.defaultBlockState());
            for (BlockPos bars : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
                placeGameplay(level, state, bars, Blocks.IRON_BARS.defaultBlockState());
            }
        }

        for (int[] offset : defaultLootOffsets()) {
            placeGameplay(level, state, base.offset(offset[0], 1, offset[1]), ModBlocks.HOTBAR_STORAGE.defaultBlockState());
        }

        for (int[] offset : new int[][] { { 12, 7 }, { 30, 7 }, { 12, 23 }, { 30, 23 }, { 12, 39 }, { 30, 39 },
                { 20, 35 }, { 24, 35 } }) {
            placeGameplay(level, state, base.offset(offset[0], 1, offset[1]), ModBlocks.REPAIR_PALLET.defaultBlockState());
        }

        placeGameplay(level, state, base.offset(22, 0, 56), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());
        placeGameplay(level, state, base.offset(43, 0, 22), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());


        // Basement/boiler and attic markers without requiring schema changes.
        for (int x = 18; x <= 26; x++) {
            for (int z = 18; z <= 26; z++) {
                placeGameplay(level, state, base.offset(x, -5, z), Blocks.STONE_BRICKS.defaultBlockState());
                for (int y = -4; y <= -1; y++) {
                    boolean wall = x == 18 || x == 26 || z == 18 || z == 26;
                    placeGameplay(level, state, base.offset(x, y, z), wall ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        placeGameplay(level, state, base.offset(22, -4, 22), Blocks.BLAST_FURNACE.defaultBlockState());
        placeGameplay(level, state, base.offset(22, 7, 50), ModBlocks.HOTBAR_STORAGE.defaultBlockState());
        placeGameplay(level, state, base.offset(21, 6, 50), Blocks.LADDER.defaultBlockState());

        buildCourtyard(level, state, base);

        for (int x = 5; x <= 39; x += 8) {
            for (int z = 6; z <= 50; z += 10) {
                placeGameplay(level, state, base.offset(x, 5, z),
                        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        for (int[] light : new int[][] { { 7, 4, 7 }, { 22, 4, 8 }, { 36, 4, 8 }, { 7, 4, 22 },
                { 22, 4, 22 }, { 36, 4, 22 }, { 7, 4, 36 }, { 22, 4, 36 }, { 36, 4, 36 },
                { 7, 4, 49 }, { 22, 4, 49 }, { 36, 4, 49 } }) {
            placeGameplay(level, state, base.offset(light[0], light[1], light[2]),
                    Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    static List<int[]> defaultLootOffsets() {
        return List.of(new int[] { 5, 6 }, new int[] { 12, 11 }, new int[] { 21, 8 },
                new int[] { 32, 6 }, new int[] { 39, 11 }, new int[] { 5, 20 }, new int[] { 21, 20 },
                new int[] { 39, 20 }, new int[] { 5, 28 }, new int[] { 21, 28 }, new int[] { 39, 28 },
                new int[] { 5, 38 }, new int[] { 21, 38 }, new int[] { 39, 38 }, new int[] { 8, 50 },
                new int[] { 22, 50 }, new int[] { 36, 50 }, new int[] { 22, 7 }, new int[] { 10, 6 },
                new int[] { 34, 6 }, new int[] { 10, 22 }, new int[] { 34, 22 }, new int[] { 10, 36 },
                new int[] { 34, 36 }, new int[] { 12, 50 }, new int[] { 32, 50 });
    }

    private static void doorway(ServerLevel level, ArenaState state, BlockPos base, int x, int z, boolean northSouthWall) {
        doorway(level, state, base, x, z, northSouthWall, true);
    }

    private static void doorway(ServerLevel level, ArenaState state, BlockPos base, int x, int z,
            boolean northSouthWall, boolean ironDoor) {
        BlockPos lower = base.offset(x, 1, z);
        clearDoorSpace(level, state, lower, northSouthWall);
        BlockState door = (ironDoor ? Blocks.IRON_DOOR : TMMBlocks.SMALL_WOOD_DOOR).defaultBlockState();
        if (northSouthWall) {
            door = door.setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
            placeGameplay(level, state, lower.west(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            placeGameplay(level, state, lower.east(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        } else {
            door = door.setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.EAST);
            placeGameplay(level, state, lower.north(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            placeGameplay(level, state, lower.south(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
        placeGameplay(level, state, lower, door);
        placeGameplay(level, state, lower.above(), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        placeGameplay(level, state, lower.above(2), Blocks.AIR.defaultBlockState());
    }

    private static void clearDoorSpace(ServerLevel level, ArenaState state, BlockPos lower, boolean northSouthWall) {
        List<BlockPos> spaces = northSouthWall
                ? List.of(lower.north(), lower.north().above(), lower.south(), lower.south().above())
                : List.of(lower.west(), lower.west().above(), lower.east(), lower.east().above());
        for (BlockPos pos : spaces) {
            placeGameplay(level, state, pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static void clearPassage(ServerLevel level, ArenaState state, BlockPos center, boolean alongX, int width) {
        int radius = width / 2;
        for (int i = -radius; i <= radius; i++) {
            for (int y = 1; y <= 3; y++) {
                placeGameplay(level, state, alongX ? center.offset(i, y - 1, 0) : center.offset(0, y - 1, i),
                        Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void clearGameplayVolume(ServerLevel level, ArenaState state, BlockPos min, BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    placeGameplay(level, state, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void placeRoomDetails(ServerLevel level, ArenaState state, BlockPos base) {
        // Kitchen and storage.
        for (int z = 4; z <= 11; z += 2) {
            placeGameplay(level, state, base.offset(3, 1, z), Blocks.BLAST_FURNACE.defaultBlockState());
            placeGameplay(level, state, base.offset(11, 1, z), Blocks.BARREL.defaultBlockState());
        }
        for (int x = 5; x <= 10; x++) {
            placeGameplay(level, state, base.offset(x, 1, 10), Blocks.SPRUCE_SLAB.defaultBlockState());
        }

        // Study shelves form cover without sealing the route.
        for (int z = 5; z <= 25; z += 5) {
            placeGameplay(level, state, base.offset(31, 1, z), Blocks.BOOKSHELF.defaultBlockState());
            placeGameplay(level, state, base.offset(40, 1, z), Blocks.BOOKSHELF.defaultBlockState());
            placeGameplay(level, state, base.offset(31, 2, z), Blocks.BOOKSHELF.defaultBlockState());
            placeGameplay(level, state, base.offset(40, 2, z), Blocks.BOOKSHELF.defaultBlockState());
        }
        placeGameplay(level, state, base.offset(36, 1, 11), Blocks.CRAFTING_TABLE.defaultBlockState());
        placeGameplay(level, state, base.offset(37, 1, 11), Blocks.DARK_OAK_STAIRS.defaultBlockState());

        // Infirmary and sleeping area.
        placeGameplay(level, state, base.offset(5, 1, 34), Blocks.RED_BED.defaultBlockState());
        placeGameplay(level, state, base.offset(10, 1, 34), Blocks.WHITE_BED.defaultBlockState());
        placeGameplay(level, state, base.offset(5, 1, 42), Blocks.WATER_CAULDRON.defaultBlockState());
        placeGameplay(level, state, base.offset(10, 1, 42), Blocks.BARREL.defaultBlockState());

        // Workshop and power room.
        placeGameplay(level, state, base.offset(33, 1, 35), Blocks.SMITHING_TABLE.defaultBlockState());
        placeGameplay(level, state, base.offset(38, 1, 35), Blocks.ANVIL.defaultBlockState());
        placeGameplay(level, state, base.offset(33, 1, 41), Blocks.COPPER_BLOCK.defaultBlockState());
        placeGameplay(level, state, base.offset(38, 1, 41), Blocks.LIGHTNING_ROD.defaultBlockState());

        // Central hall landmarks.
        for (int x = 19; x <= 25; x++) {
            placeGameplay(level, state, base.offset(x, 1, 27), Blocks.DARK_OAK_SLAB.defaultBlockState());
        }
        placeGameplay(level, state, base.offset(22, 2, 27), Blocks.SOUL_LANTERN.defaultBlockState());
        placeGameplay(level, state, base.offset(20, 1, 33), Blocks.FLOWER_POT.defaultBlockState());
        placeGameplay(level, state, base.offset(24, 1, 33), Blocks.FLOWER_POT.defaultBlockState());
    }

    private static void buildCourtyard(ServerLevel level, ArenaState state, BlockPos base) {
        for (int x = -5; x <= 49; x++) {
            for (int z = -5; z <= 61; z++) {
                if (x >= 0 && x <= 44 && z >= 0 && z <= 56) {
                    continue;
                }
                placeGameplay(level, state, base.offset(x, 0, z), Blocks.GRAVEL.defaultBlockState());
            }
        }
        for (int x = -5; x <= 49; x++) {
            placeGameplay(level, state, base.offset(x, 1, -5), Blocks.OAK_LEAVES.defaultBlockState());
            placeGameplay(level, state, base.offset(x, 1, 61), Blocks.OAK_LEAVES.defaultBlockState());
        }
        for (int z = -4; z <= 60; z++) {
            placeGameplay(level, state, base.offset(-5, 1, z), Blocks.OAK_LEAVES.defaultBlockState());
            placeGameplay(level, state, base.offset(49, 1, z), Blocks.OAK_LEAVES.defaultBlockState());
        }
        clearPassage(level, state, base.offset(22, 1, 61), true, 5);
        clearPassage(level, state, base.offset(-5, 1, 22), false, 3);
        clearPassage(level, state, base.offset(49, 1, 22), false, 3);
        clearPassage(level, state, base.offset(22, 1, -5), true, 3);
        placeGameplay(level, state, base.offset(12, 1, -3), Blocks.SPRUCE_STAIRS.defaultBlockState());
        placeGameplay(level, state, base.offset(13, 1, -3), Blocks.SPRUCE_SLAB.defaultBlockState());
        placeGameplay(level, state, base.offset(14, 1, -3), Blocks.SPRUCE_STAIRS.defaultBlockState());
        placeGameplay(level, state, base.offset(31, 1, 59), Blocks.SPRUCE_STAIRS.defaultBlockState());
        placeGameplay(level, state, base.offset(32, 1, 59), Blocks.SPRUCE_SLAB.defaultBlockState());
        placeGameplay(level, state, base.offset(33, 1, 59), Blocks.SPRUCE_STAIRS.defaultBlockState());
    }

    private static void buildConfiguredGameplay(ServerLevel level, ArenaState state, MapConfig.RepairConfig config) {
        for (MapConfig.CloneEntry clone : config.cloneEntries) {
            cloneBlocks(level, state, clone.source.toBlockPos(), clone.target.toBlockPos(), clone.size.toBlockPos());
        }
        for (MapConfig.Pos pos : config.repairStations) {
            placeGameplay(level, state, pos.toBlockPos(), ModBlocks.REPAIR_STATION.defaultBlockState());
        }
        for (MapConfig.Pos pos : config.trialStands) {
            placeGameplay(level, state, pos.toBlockPos(), ModBlocks.HUNTER_CAGE.defaultBlockState());
        }
        for (MapConfig.LootPointEntry point : config.lootPoints) {
            BlockPos pos = point.pos.toBlockPos();
            if (level.getBlockState(pos).canBeReplaced()) {
                placeGameplay(level, state, pos, ModBlocks.HOTBAR_STORAGE.defaultBlockState());
            } else {
                snapshot(level, state.gameplayBlocks, pos);
            }
        }
    }

    private static void cloneBlocks(ServerLevel level, ArenaState state, BlockPos source, BlockPos target, BlockPos size) {
        int dx = Math.max(1, Math.abs(size.getX()));
        int dy = Math.max(1, Math.abs(size.getY()));
        int dz = Math.max(1, Math.abs(size.getZ()));
        for (int x = 0; x < dx; x++) {
            for (int y = 0; y < dy; y++) {
                for (int z = 0; z < dz; z++) {
                    BlockPos src = source.offset(x, y, z);
                    BlockPos dst = target.offset(x, y, z);
                    snapshot(level, state.gameplayBlocks, dst);
                    BlockState blockState = level.getBlockState(src);
                    CompoundTag blockEntityTag = null;
                    BlockEntity sourceEntity = level.getBlockEntity(src);
                    if (sourceEntity != null) {
                        blockEntityTag = sourceEntity.saveWithFullMetadata(level.registryAccess());
                    }
                    level.setBlock(dst, blockState, Block.UPDATE_ALL);
                    if (blockEntityTag != null && level.getBlockEntity(dst) instanceof BlockEntity targetEntity) {
                        CompoundTag tag = blockEntityTag.copy();
                        tag.putInt("x", dst.getX());
                        tag.putInt("y", dst.getY());
                        tag.putInt("z", dst.getZ());
                        targetEntity.loadWithComponents(tag, level.registryAccess());
                        targetEntity.setChanged();
                    }
                    level.getLightEngine().checkBlock(dst);
                }
            }
        }
    }

    private static void ruinCluster(ServerLevel level, ArenaState state, BlockPos origin, int side) {
        for (int i = 1; i <= 3; i++) {
            BlockPos p = origin.offset(side * (2 + i), 0, i - 2);
            placeGameplay(level, state, p, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
            if (i % 2 == 0) {
                placeGameplay(level, state, p.above(), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        placeGameplay(level, state, origin.offset(side * 2, 0, -2), Blocks.LANTERN.defaultBlockState());
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static void placeSelection(ServerLevel level, ArenaState state, BlockPos pos, BlockState blockState) {
        snapshot(level, state.selectionBlocks, pos);
        level.setBlock(pos, blockState, Block.UPDATE_ALL);
        level.getLightEngine().checkBlock(pos);
    }

    private static void placeGameplay(ServerLevel level, ArenaState state, BlockPos pos, BlockState blockState) {
        snapshot(level, state.gameplayBlocks, pos);
        level.setBlock(pos, blockState, Block.UPDATE_ALL);
        level.getLightEngine().checkBlock(pos);
    }

    private static void snapshot(ServerLevel level, LinkedHashMap<BlockPos, BlockSnapshot> snapshots, BlockPos pos) {
        if (snapshots.containsKey(pos)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag tag = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
        snapshots.put(pos.immutable(), new BlockSnapshot(level.getBlockState(pos), tag));
    }

    private static void restoreBlocks(ServerLevel level, LinkedHashMap<BlockPos, BlockSnapshot> snapshots) {
        List<Map.Entry<BlockPos, BlockSnapshot>> entries = new ArrayList<>(snapshots.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<BlockPos, BlockSnapshot> entry = entries.get(i);
            BlockPos pos = entry.getKey();
            BlockSnapshot snapshot = entry.getValue();
            level.setBlock(pos, snapshot.state, Block.UPDATE_ALL);
            if (snapshot.blockEntityTag != null) {
                BlockEntity restored = level.getBlockEntity(pos);
                if (restored != null) {
                    CompoundTag tag = snapshot.blockEntityTag.copy();
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    restored.loadWithComponents(tag, level.registryAccess());
                    restored.setChanged();
                }
            }
            level.getLightEngine().checkBlock(pos);
        }
        snapshots.clear();
    }

    private static final class ArenaState {
        private final LinkedHashMap<BlockPos, BlockSnapshot> selectionBlocks = new LinkedHashMap<>();
        private final LinkedHashMap<BlockPos, BlockSnapshot> gameplayBlocks = new LinkedHashMap<>();
        private final Map<UUID, PlayerSlot> playerSlots = new HashMap<>();
        private final List<ArmorStand> seats = new ArrayList<>();
        private BlockPos selectionCenter = BlockPos.ZERO;
        private boolean selectionRestored;
    }

    private record BlockSnapshot(BlockState state, CompoundTag blockEntityTag) {
    }

    private record PlayerSlot(double originalX, double originalY, double originalZ, float originalYaw,
            float originalPitch, double x, double y, double z, float yaw, float pitch) {
    }
}
