package net.exmo.sre.repair.arena;

import net.exmo.sre.repair.*;
import net.exmo.sre.repair.role.*;
import net.exmo.sre.repair.state.*;
import net.exmo.sre.repair.event.*;
import net.exmo.sre.repair.util.*;

import io.wifi.starrailexpress.game.data.MapConfig;
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
import org.agmas.noellesroles.init.ModBlocks;

import java.util.*;

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
        int[][] spawns = hunter ? RepairManorScene.hunterSpawns() : RepairManorScene.survivorSpawns();
        int[] offset = spawns[Math.floorMod(index, spawns.length)];
        BlockPos pos = base.offset(offset[0], offset[1], offset[2]);
        // 追捕者在墓地面向庄园（-Z），幸存者在庄园南侧面向大厅（+Z）
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

    static List<int[]> defaultLootOffsets() {
        return RepairManorScene.lootOffsets();
    }

    public static BlockPos defaultMansionBase(ServerLevel level) {
        return DEFAULT_MANSION_BASE;
    }

    private static void buildDefaultMansionTemplate(ServerLevel level, ArenaState state) {
        RepairManorScene.build((pos, blockState) -> placeGameplay(level, state, pos, blockState),
                defaultMansionBase(level));
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
