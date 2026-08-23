/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.api.time;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.api.time.TimeRewindAreaResult.Failure;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot.DoorState;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot.ItemState;
import org.agmas.noellesroles.game.c4.C4Detonation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Internal implementation behind the area snapshot methods on {@link TimeRewind}. */
final class TimeRewindAreaEngine {
    private static final int DOOR_RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private TimeRewindAreaEngine() {
    }

    static TimeRewindAreaSnapshot capture(ServerLevel level, AABB area) {
        List<ItemState> items = new ArrayList<>();
        List<DoorState> doors = new ArrayList<>();
        List<TimeRewindAreaSnapshot.Warning> warnings = new ArrayList<>();

        List<ItemEntity> currentItems = level.getEntitiesOfClass(ItemEntity.class, area,
                item -> !item.isRemoved());
        currentItems.sort(Comparator.comparing(Entity::getUUID));
        for (ItemEntity item : currentItems) {
            try {
                CompoundTag data = item.saveWithoutId(new CompoundTag());
                ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(item.getType());
                data.putString("id", typeId.toString());
                items.add(new ItemState(item.getUUID(), data));
            } catch (RuntimeException exception) {
                warnings.add(new TimeRewindAreaSnapshot.Warning("item", item.getUUID().toString(),
                        describe(exception)));
                Noellesroles.LOGGER.warn("Failed to capture dropped item {} for area rewind",
                        item.getUUID(), exception);
            }
        }

        for (SmallDoorBlockEntity door : findDoors(level, area)) {
            BlockPos lowerPos = door.getBlockPos();
            try {
                BlockState lowerState = level.getBlockState(lowerPos);
                if (!(lowerState.getBlock() instanceof SmallDoorBlock)) {
                    continue;
                }
                CompoundTag data = door.saveWithFullMetadata(level.registryAccess());
                doors.add(new DoorState(lowerPos, lowerState, level.getBlockState(lowerPos.above()),
                        data, door.getCooldown()));
            } catch (RuntimeException exception) {
                warnings.add(new TimeRewindAreaSnapshot.Warning("small_door", lowerPos.toShortString(),
                        describe(exception)));
                Noellesroles.LOGGER.warn("Failed to capture SmallDoor at {} for area rewind",
                        lowerPos, exception);
            }
        }

        return new TimeRewindAreaSnapshot(level.dimension(), area, items, doors,
                C4Detonation.snapshotForTimeRewind(), warnings);
    }

    static TimeRewindAreaResult restore(ServerLevel level, TimeRewindAreaSnapshot snapshot) {
        List<Failure> failures = new ArrayList<>();
        snapshot.warnings().forEach(warning -> failures.add(
                new Failure("capture_" + warning.scope(), warning.subject(), warning.message())));

        Set<UUID> removedItemIds = removeCurrentItems(level, snapshot);
        int restoredDoors = restoreDoors(level, snapshot, failures);
        int restoredItems = restoreItems(level, snapshot, failures);
        C4Detonation.restoreForTimeRewind(snapshot.rawC4State());
        return new TimeRewindAreaResult(restoredItems, restoredDoors, removedItemIds.size(), failures);
    }

    private static List<SmallDoorBlockEntity> findDoors(ServerLevel level, AABB area) {
        int minChunkX = SectionPos.blockToSectionCoord((int) Math.floor(area.minX));
        int maxChunkX = SectionPos.blockToSectionCoord((int) Math.floor(area.maxX));
        int minChunkZ = SectionPos.blockToSectionCoord((int) Math.floor(area.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord((int) Math.floor(area.maxZ));
        List<SmallDoorBlockEntity> doors = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SmallDoorBlockEntity door
                            && area.contains(door.getBlockPos().getCenter())) {
                        doors.add(door);
                    }
                }
            }
        }
        doors.sort(Comparator.comparingLong(door -> door.getBlockPos().asLong()));
        return doors;
    }

    private static Set<UUID> removeCurrentItems(ServerLevel level, TimeRewindAreaSnapshot snapshot) {
        Set<UUID> removed = new HashSet<>();

        // A captured item may have travelled outside the area or through a portal.
        // Remove that exact entity first so its UUID can be reused safely.
        for (ItemState itemState : snapshot.rawItemStates()) {
            for (ServerLevel candidate : level.getServer().getAllLevels()) {
                Entity entity = candidate.getEntity(itemState.entityId());
                if (entity instanceof ItemEntity item && removed.add(item.getUUID())) {
                    item.discard();
                }
            }
        }

        // Items created after the snapshot are not part of the restored area state.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, snapshot.area(),
                candidate -> !candidate.isRemoved())) {
            if (removed.add(item.getUUID())) {
                item.discard();
            }
        }
        return removed;
    }

    private static int restoreItems(ServerLevel level, TimeRewindAreaSnapshot snapshot,
            List<Failure> failures) {
        int restored = 0;
        for (ItemState itemState : snapshot.rawItemStates()) {
            try {
                CompoundTag data = itemState.copyData();
                ResourceLocation typeId = ResourceLocation.tryParse(data.getString("id"));
                EntityType<?> type = typeId == null
                        ? null
                        : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
                if (type == null) {
                    throw new IllegalStateException("item entity type is no longer registered");
                }
                Entity entity = type.create(level);
                if (!(entity instanceof ItemEntity item)) {
                    throw new IllegalStateException("snapshot entity type is not an ItemEntity");
                }
                item.load(data);
                if (!level.addFreshEntity(item)) {
                    throw new IllegalStateException("server rejected restored item entity");
                }
                restored++;
            } catch (RuntimeException exception) {
                failures.add(new Failure("item", itemState.entityId().toString(), describe(exception)));
                Noellesroles.LOGGER.warn("Failed to restore dropped item {} during area rewind",
                        itemState.entityId(), exception);
            }
        }
        return restored;
    }

    private static int restoreDoors(ServerLevel level, TimeRewindAreaSnapshot snapshot,
            List<Failure> failures) {
        int restored = 0;
        for (DoorState doorState : snapshot.rawDoorStates()) {
            BlockPos lowerPos = doorState.lowerPos();
            try {
                level.setBlock(lowerPos, doorState.lowerState(), DOOR_RESTORE_FLAGS);
                level.setBlock(lowerPos.above(), doorState.upperState(), DOOR_RESTORE_FLAGS);
                if (!(level.getBlockEntity(lowerPos) instanceof SmallDoorBlockEntity door)) {
                    throw new IllegalStateException("SmallDoor block entity could not be recreated");
                }

                CompoundTag data = doorState.copyData();
                data.putInt("x", lowerPos.getX());
                data.putInt("y", lowerPos.getY());
                data.putInt("z", lowerPos.getZ());
                door.loadWithComponents(data, level.registryAccess());
                door.setCooldown(doorState.cooldown());
                door.sync();
                door.syncBlockEvent(1, door.isOpen() ? 1 : 0);
                restored++;
            } catch (RuntimeException exception) {
                failures.add(new Failure("small_door", lowerPos.toShortString(), describe(exception)));
                Noellesroles.LOGGER.warn("Failed to restore SmallDoor at {} during area rewind",
                        lowerPos, exception);
            }
        }
        return restored;
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
