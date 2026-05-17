package org.agmas.noellesroles.game.modes.repair;

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

    private RepairArenaBuilder() {
    }

    public static void prepare(ServerLevel level, List<ServerPlayer> players) {
        restoreAll(level);
        ArenaState state = new ArenaState();
        ARENAS.put(level, state);
        buildSelectionRoom(level, state, players);
        buildGameplayStructures(level, state);
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
        BlockPos spawn = level.getSharedSpawnPos();
        int baseX = spawn.getX() + 96;
        int baseZ = spawn.getZ() + 96;
        int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX, baseZ) + 14;
        BlockPos base = new BlockPos(baseX, baseY, baseZ);
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

    private static void buildGameplayStructures(ServerLevel level, ArenaState state) {
        BlockPos center = level.getSharedSpawnPos();
        int[][] stationOffsets = {
                { 0, -18 }, { 17, -7 }, { 14, 16 }, { -14, 16 }, { -17, -7 }
        };
        for (int[] offset : stationOffsets) {
            BlockPos pos = surface(level, center.getX() + offset[0], center.getZ() + offset[1]);
            placeGameplay(level, state, pos, ModBlocks.REPAIR_STATION.defaultBlockState());
            ruinCluster(level, state, pos, offset[0] < 0 ? -1 : 1);
        }

        int[][] cageOffsets = {
                { 0, 12 }, { -19, 0 }, { 19, 0 }
        };
        for (int[] offset : cageOffsets) {
            BlockPos pos = surface(level, center.getX() + offset[0], center.getZ() + offset[1]);
            placeGameplay(level, state, pos, ModBlocks.HUNTER_CAGE.defaultBlockState());
            placeGameplay(level, state, pos.offset(1, 0, 0), Blocks.IRON_BARS.defaultBlockState());
            placeGameplay(level, state, pos.offset(-1, 0, 0), Blocks.IRON_BARS.defaultBlockState());
        }

        int[][] gateOffsets = {
                { 0, -30 }, { 0, 30 }
        };
        for (int[] offset : gateOffsets) {
            BlockPos pos = surface(level, center.getX() + offset[0], center.getZ() + offset[1]);
            placeGameplay(level, state, pos, ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());
            placeGameplay(level, state, pos.above(), Blocks.IRON_BARS.defaultBlockState());
        }

        int[][] supplyOffsets = {
                { -8, -13 }, { 8, -13 }, { 20, 8 }, { -20, 8 }, { 10, 23 }, { -10, 23 }
        };
        for (int[] offset : supplyOffsets) {
            BlockPos pos = surface(level, center.getX() + offset[0], center.getZ() + offset[1]);
            placeGameplay(level, state, pos, ModBlocks.REPAIR_SUPPLY_CRATE.defaultBlockState());
        }

        int[][] palletOffsets = {
                { -6, -5 }, { 6, -5 }, { -6, 6 }, { 6, 6 }, { -22, -10 }, { 22, -10 }, { -22, 16 }, { 22, 16 }
        };
        for (int[] offset : palletOffsets) {
            BlockPos pos = surface(level, center.getX() + offset[0], center.getZ() + offset[1]);
            placeGameplay(level, state, pos, ModBlocks.REPAIR_PALLET.defaultBlockState());
        }

        for (int i = -3; i <= 3; i++) {
            BlockPos wall = surface(level, center.getX() + i * 4, center.getZ());
            placeGameplay(level, state, wall, Blocks.STONE_BRICKS.defaultBlockState());
            placeGameplay(level, state, wall.above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
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
