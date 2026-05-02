package io.wifi.events.day_night_fight.cca;

import io.wifi.events.day_night_fight.block.CluePointBlock;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DNFWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<DNFWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_world"), DNFWorldComponent.class);

    private final Level world;
    private final PhaseState phase = new PhaseState();
    private final WaterState water = new WaterState();
    private final MeetingState meeting = new MeetingState();
    private final MapState map = new MapState();
    private final UnderworldState underworld = new UnderworldState();
    private boolean syncQueued;

    public DNFWorldComponent(Level world) {
        this.world = world;
    }

    // ========== DNF游戏状态方法 ==========

    public void resetRoundState() {
        phase.reset();
        water.reset();
        meeting.reset();
        sync();
    }

    public void setPhase(int day, boolean isNight) {
        phase.currentDay = Math.max(0, day);
        phase.night = isNight;
        sync();
    }

    public void rollWaterPoisonToToday() {
        water.rollTomorrowToToday();
        sync();
    }

    public void sync() {
        syncQueued = true;
    }

    @Override
    public void serverTick() {
        if (!syncQueued) {
            return;
        }
        syncQueued = false;
        KEY.sync(world);
    }

    public int getCurrentDay() {
        return phase.currentDay;
    }

    public boolean isNight() {
        return phase.night;
    }

    public boolean isTrueEndingTriggered() {
        return phase.trueEndingTriggered;
    }

    public void setTrueEndingTriggered(boolean trueEndingTriggered) {
        this.phase.trueEndingTriggered = trueEndingTriggered;
        sync();
    }

    public boolean isWaterPoisonedToday() {
        return water.poisonedToday;
    }

    @Nullable
    public UUID getWaterPoisonerToday() {
        return water.poisonerToday;
    }

    public void clearWaterPoison() {
        water.reset();
        sync();
    }

    public void poisonWaterTomorrow(UUID poisoner) {
        water.poisonedTomorrow = true;
        water.poisonerTomorrow = poisoner;
        sync();
    }

    public boolean isMeetingActive() {
        return meeting.active;
    }

    public void setMeetingActive(boolean meetingActive) {
        this.meeting.active = meetingActive;
        sync();
    }

    @Nullable
    public UUID getVotedTarget() {
        return meeting.votedTarget;
    }

    public void setVotedTarget(@Nullable UUID votedTarget) {
        this.meeting.votedTarget = votedTarget;
        sync();
    }

    public void clearVote() {
        meeting.reset();
        sync();
    }

    @Nullable
    public BlockPos getFoodBoxPos() {
        return map.foodBoxPos;
    }

    public void setFoodBoxPos(@Nullable BlockPos foodBoxPos) {
        map.foodBoxPos = foodBoxPos == null ? null : foodBoxPos.immutable();
        sync();
    }

    @Nullable
    public BlockPos getWaterSourcePos() {
        return map.waterSourcePos;
    }

    public void setWaterSourcePos(@Nullable BlockPos waterSourcePos) {
        map.waterSourcePos = waterSourcePos == null ? null : waterSourcePos.immutable();
        sync();
    }

    @Nullable
    public BlockPos getMeteorPos() {
        return map.meteorPos;
    }

    public void setMeteorPos(@Nullable BlockPos meteorPos) {
        map.meteorPos = meteorPos == null ? null : meteorPos.immutable();
        sync();
    }

    @Nullable
    public BlockPos getWallHolePos() {
        return map.wallHolePos;
    }

    public void setWallHolePos(@Nullable BlockPos wallHolePos) {
        map.wallHolePos = wallHolePos == null ? null : wallHolePos.immutable();
        sync();
    }

    @Nullable
    public AABB getCafeteriaArea() {
        return map.cafeteriaArea;
    }

    public void setCafeteriaArea(@Nullable AABB cafeteriaArea) {
        map.cafeteriaArea = cafeteriaArea;
        sync();
    }

    public void setDormRoom(UUID player, AABB room) {
        map.dormRooms.put(player, room);
        sync();
    }

    @Nullable
    public AABB getDormRoom(UUID player) {
        return map.dormRooms.get(player);
    }

    public boolean isFoodBox(BlockPos pos) {
        return map.foodBoxPos != null && map.foodBoxPos.equals(pos);
    }

    public boolean isWaterSource(BlockPos pos) {
        return map.waterSourcePos != null && map.waterSourcePos.equals(pos);
    }

    public boolean isMeteor(BlockPos pos) {
        return map.meteorPos != null && map.meteorPos.equals(pos);
    }

    public boolean isWallHole(BlockPos pos) {
        return map.wallHolePos != null && map.wallHolePos.equals(pos);
    }

    @Nullable
    public Container getFoodBoxContainer() {
        return getContainer(map.foodBoxPos);
    }

    @Nullable
    public Container getWallHoleContainer() {
        return getContainer(map.wallHolePos);
    }

    @Nullable
    private Container getContainer(@Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    public boolean addToFoodBox(ItemStack stack) {
        return addToContainer(getFoodBoxContainer(), stack);
    }

    public static boolean addToContainer(@Nullable Container container, ItemStack stack) {
        if (container == null || stack.isEmpty()) {
            return false;
        }
        if (!canFit(container, stack)) {
            return false;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, remaining)
                    && current.getCount() < current.getMaxStackSize()) {
                int move = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                current.grow(move);
                remaining.shrink(move);
                container.setChanged();
                if (remaining.isEmpty()) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                int move = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.copy();
                placed.setCount(move);
                container.setItem(slot, placed);
                remaining.shrink(move);
                container.setChanged();
                if (remaining.isEmpty()) {
                    return true;
                }
            }
        }
        stack.setCount(remaining.getCount());
        return false;
    }

    public static boolean canFit(Container container, ItemStack stack) {
        int capacity = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack current = container.getItem(slot);
            if (current.isEmpty()) {
                capacity += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                capacity += Math.max(0, current.getMaxStackSize() - current.getCount());
            }
            if (capacity >= stack.getCount()) {
                return true;
            }
        }
        return false;
    }

    // ========== 里世界系统方法 ==========

    public void setUnderworldCenter(BlockPos center) {
        underworld.center = center.immutable();
        sync();
    }

    public void setUnderworldRadius(double radius) {
        underworld.radius = radius;
        sync();
    }

    public BlockPos getUnderworldCenter() {
        return underworld.center;
    }

    public double getUnderworldRadius() {
        return underworld.radius;
    }

    public BlockPos generateCluePoint(Level world) {
        double angle = Math.random() * Math.PI * 2;
        double distance = Math.random() * underworld.radius;
        
        int x = underworld.center.getX() + (int)(Math.cos(angle) * distance);
        int z = underworld.center.getZ() + (int)(Math.sin(angle) * distance);
        int y = underworld.center.getY();
        
        BlockPos pos = new BlockPos(x, y, z);
        BlockPos groundPos = world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
        
        BlockPos cluePoint = groundPos;
        underworld.activeCluePoints.add(cluePoint);
        
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(cluePoint, CluePointBlock.cluePointBlock().defaultBlockState(), 3);
        }
        
        sync();
        return cluePoint;
    }

    public BlockPos findRandomUnderworldSpawn(ServerLevel level) {
        BlockPos fallback = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, underworld.center);
        double radius = Math.max(1.0, underworld.radius);

        for (int i = 0; i < 32; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(level.random.nextDouble()) * radius;
            int x = underworld.center.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = underworld.center.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos probe = new BlockPos(x, underworld.center.getY(), z);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            fallback = pos;
            if (level.getWorldBorder().isWithinBounds(pos) && !level.getBlockState(pos.below()).isAir()
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos.immutable();
            }
        }

        return fallback.immutable();
    }

    public void removeCluePoint(BlockPos pos) {
        underworld.activeCluePoints.removeIf(p -> p.equals(pos));
        sync();
    }

    public List<BlockPos> getActiveCluePoints() {
        return underworld.activeCluePoints;
    }

    public void clearCluePoints() {
        underworld.activeCluePoints.clear();
        sync();
    }

    // ========== NBT序列化 ==========

    private static void putBlockPos(CompoundTag tag, String key, @Nullable BlockPos pos) {
        if (pos == null) {
            return;
        }
        CompoundTag posTag = new CompoundTag();
        posTag.putInt("X", pos.getX());
        posTag.putInt("Y", pos.getY());
        posTag.putInt("Z", pos.getZ());
        tag.put(key, posTag);
    }

    @Nullable
    private static BlockPos getBlockPos(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag posTag = tag.getCompound(key);
        return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
    }

    private static void putBox(CompoundTag tag, String key, @Nullable AABB box) {
        if (box == null) {
            return;
        }
        CompoundTag boxTag = new CompoundTag();
        boxTag.putDouble("MinX", box.minX);
        boxTag.putDouble("MinY", box.minY);
        boxTag.putDouble("MinZ", box.minZ);
        boxTag.putDouble("MaxX", box.maxX);
        boxTag.putDouble("MaxY", box.maxY);
        boxTag.putDouble("MaxZ", box.maxZ);
        tag.put(key, boxTag);
    }

    @Nullable
    private static AABB getBox(CompoundTag tag, String key) {
        if (!tag.contains(key, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag boxTag = tag.getCompound(key);
        return new AABB(boxTag.getDouble("MinX"), boxTag.getDouble("MinY"), boxTag.getDouble("MinZ"),
                boxTag.getDouble("MaxX"), boxTag.getDouble("MaxY"), boxTag.getDouble("MaxZ"));
    }

    private static final class PhaseState {
        private int currentDay;
        private boolean night;
        private boolean trueEndingTriggered;

        private void reset() {
            currentDay = 0;
            night = false;
            trueEndingTriggered = false;
        }

        private void write(CompoundTag tag) {
            tag.putInt("CurrentDay", currentDay);
            tag.putBoolean("Night", night);
            tag.putBoolean("TrueEndingTriggered", trueEndingTriggered);
        }

        private void read(CompoundTag tag) {
            currentDay = tag.getInt("CurrentDay");
            night = tag.getBoolean("Night");
            trueEndingTriggered = tag.getBoolean("TrueEndingTriggered");
        }
    }

    private static final class WaterState {
        private boolean poisonedToday;
        private boolean poisonedTomorrow;
        @Nullable
        private UUID poisonerToday;
        @Nullable
        private UUID poisonerTomorrow;

        private void reset() {
            poisonedToday = false;
            poisonedTomorrow = false;
            poisonerToday = null;
            poisonerTomorrow = null;
        }

        private void rollTomorrowToToday() {
            poisonedToday = poisonedTomorrow;
            poisonerToday = poisonerTomorrow;
            poisonedTomorrow = false;
            poisonerTomorrow = null;
        }

        private void write(CompoundTag tag) {
            writePublic(tag);
            tag.putBoolean("WaterPoisonedTomorrow", poisonedTomorrow);
            if (poisonerTomorrow != null) {
                tag.putUUID("WaterPoisonerTomorrow", poisonerTomorrow);
            }
        }

        private void read(CompoundTag tag) {
            readPublic(tag);
            poisonedTomorrow = tag.getBoolean("WaterPoisonedTomorrow");
            poisonerTomorrow = tag.contains("WaterPoisonerTomorrow") ? tag.getUUID("WaterPoisonerTomorrow") : null;
        }

        private void writePublic(CompoundTag tag) {
            tag.putBoolean("WaterPoisonedToday", poisonedToday);
            if (poisonerToday != null) {
                tag.putUUID("WaterPoisonerToday", poisonerToday);
            }
        }

        private void readPublic(CompoundTag tag) {
            poisonedToday = tag.getBoolean("WaterPoisonedToday");
            poisonerToday = tag.contains("WaterPoisonerToday") ? tag.getUUID("WaterPoisonerToday") : null;
        }
    }

    private static final class MeetingState {
        private boolean active;
        @Nullable
        private UUID votedTarget;

        private void reset() {
            active = false;
            votedTarget = null;
        }

        private void write(CompoundTag tag) {
            tag.putBoolean("MeetingActive", active);
            if (votedTarget != null) {
                tag.putUUID("VotedTarget", votedTarget);
            }
        }

        private void read(CompoundTag tag) {
            active = tag.getBoolean("MeetingActive");
            votedTarget = tag.contains("VotedTarget") ? tag.getUUID("VotedTarget") : null;
        }
    }

    private static final class MapState {
        @Nullable
        private BlockPos foodBoxPos;
        @Nullable
        private BlockPos waterSourcePos;
        @Nullable
        private BlockPos meteorPos;
        @Nullable
        private BlockPos wallHolePos;
        @Nullable
        private AABB cafeteriaArea;
        private final Map<UUID, AABB> dormRooms = new HashMap<>();

        private void write(CompoundTag tag) {
            putBlockPos(tag, "FoodBoxPos", foodBoxPos);
            putBlockPos(tag, "WaterSourcePos", waterSourcePos);
            putBlockPos(tag, "MeteorPos", meteorPos);
            putBlockPos(tag, "WallHolePos", wallHolePos);
            putBox(tag, "CafeteriaArea", cafeteriaArea);

            ListTag dormList = new ListTag();
            for (Map.Entry<UUID, AABB> entry : dormRooms.entrySet()) {
                CompoundTag dormTag = new CompoundTag();
                dormTag.putUUID("Player", entry.getKey());
                putBox(dormTag, "Room", entry.getValue());
                dormList.add(dormTag);
            }
            tag.put("DormRooms", dormList);
        }

        private void read(CompoundTag tag) {
            foodBoxPos = getBlockPos(tag, "FoodBoxPos");
            waterSourcePos = getBlockPos(tag, "WaterSourcePos");
            meteorPos = getBlockPos(tag, "MeteorPos");
            wallHolePos = getBlockPos(tag, "WallHolePos");
            cafeteriaArea = getBox(tag, "CafeteriaArea");

            dormRooms.clear();
            ListTag dormList = tag.getList("DormRooms", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < dormList.size(); i++) {
                CompoundTag dormTag = dormList.getCompound(i);
                if (dormTag.contains("Player") && dormTag.contains("Room", CompoundTag.TAG_COMPOUND)) {
                    AABB room = getBox(dormTag, "Room");
                    if (room != null) {
                        dormRooms.put(dormTag.getUUID("Player"), room);
                    }
                }
            }
        }
    }

    private static final class UnderworldState {
        private BlockPos center = new BlockPos(0, 64, 0);
        private double radius = 50.0;
        private final List<BlockPos> activeCluePoints = new ArrayList<>();

        private void write(CompoundTag tag) {
            tag.putLong("underworld_center", center.asLong());
            tag.putDouble("underworld_radius", radius);

            ListTag clueList = new ListTag();
            for (BlockPos pos : activeCluePoints) {
                clueList.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
            }
            tag.put("clue_points", clueList);
        }

        private void read(CompoundTag tag) {
            if (tag.contains("underworld_center", Tag.TAG_LONG)) {
                center = BlockPos.of(tag.getLong("underworld_center"));
            }
            if (tag.contains("underworld_radius", Tag.TAG_DOUBLE)) {
                radius = tag.getDouble("underworld_radius");
            }

            activeCluePoints.clear();
            if (tag.contains("clue_points", Tag.TAG_LIST)) {
                ListTag list = tag.getList("clue_points", Tag.TAG_LONG);
                for (int i = 0; i < list.size(); i++) {
                    activeCluePoints.add(BlockPos.of(((net.minecraft.nbt.LongTag) list.get(i)).getAsLong()));
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase.write(tag);
        water.write(tag);
        meeting.write(tag);
        map.write(tag);
        underworld.write(tag);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase.read(tag);
        water.read(tag);
        meeting.read(tag);
        map.read(tag);
        underworld.read(tag);
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @CheckEnvironment(net.fabricmc.api.EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase.write(tag);
        water.writePublic(tag);
        meeting.write(tag);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        phase.read(tag);
        water.readPublic(tag);
        meeting.read(tag);
    }
}
