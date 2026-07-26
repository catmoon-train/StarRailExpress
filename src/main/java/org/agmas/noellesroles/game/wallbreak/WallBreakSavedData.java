package org.agmas.noellesroles.game.wallbreak;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * 破墙弹被拆方块的持久化恢复表：把每个被拆方块的坐标 + 原 BlockState + 绝对恢复时刻（{@code level.getGameTime()}）
 * 写入存档（{@code <world>/data/noellesroles_wallbreak_restore.dat}），
 * 因此即便游戏中断 / 服务器重启，计划也不会丢失——{@link WallBreakManager} 在 tick 中读取本表并按时恢复。
 */
public final class WallBreakSavedData extends SavedData {
    private static final String DATA_NAME = "noellesroles_wallbreak_restore";

    /** 一个待恢复的被拆方块。 */
    public static final class Entry {
        public final ResourceLocation dimension;
        public final BlockPos pos;
        public final BlockState state;
        public final long restoreAtGameTime;

        public Entry(ResourceLocation dimension, BlockPos pos, BlockState state, long restoreAtGameTime) {
            this.dimension = dimension;
            this.pos = pos;
            this.state = state;
            this.restoreAtGameTime = restoreAtGameTime;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static WallBreakSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(WallBreakSavedData::new, WallBreakSavedData::load, null),
                DATA_NAME);
    }

    public List<Entry> entries() {
        return entries;
    }

    public void add(Entry entry) {
        entries.add(entry);
        setDirty(true);
    }

    public void remove(Entry entry) {
        entries.remove(entry);
        setDirty(true);
    }

    public static WallBreakSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WallBreakSavedData data = new WallBreakSavedData();
        HolderLookup.RegistryLookup<net.minecraft.world.level.block.Block> blocks =
                provider.lookupOrThrow(Registries.BLOCK);
        for (Tag element : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag entryTag = (CompoundTag) element;
            ResourceLocation dim = ResourceLocation.parse(entryTag.getString("Dim"));
            BlockPos pos = new BlockPos(entryTag.getInt("X"), entryTag.getInt("Y"), entryTag.getInt("Z"));
            BlockState state = NbtUtils.readBlockState(blocks, entryTag.getCompound("State"));
            long restoreAt = entryTag.getLong("RestoreAt");
            data.entries.add(new Entry(dim, pos, state, restoreAt));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Dim", entry.dimension.toString());
            entryTag.putInt("X", entry.pos.getX());
            entryTag.putInt("Y", entry.pos.getY());
            entryTag.putInt("Z", entry.pos.getZ());
            entryTag.put("State", NbtUtils.writeBlockState(entry.state));
            entryTag.putLong("RestoreAt", entry.restoreAtGameTime);
            list.add(entryTag);
        }
        tag.put("Entries", list);
        return tag;
    }
}
