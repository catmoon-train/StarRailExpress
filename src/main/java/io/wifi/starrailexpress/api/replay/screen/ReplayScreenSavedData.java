package io.wifi.starrailexpress.api.replay.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ReplayScreenSavedData extends SavedData {
    private static final String DATA_NAME = "starrailexpress_replay_screens";

    private final Map<String, ReplayScreenEntry> screens = new LinkedHashMap<>();
    private String defaultScreenId = "";

    public static ReplayScreenSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public static ReplayScreenSavedData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(ReplayScreenSavedData::new, ReplayScreenSavedData::load, null),
                DATA_NAME);
    }

    public static ReplayScreenSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ReplayScreenSavedData data = new ReplayScreenSavedData();
        data.defaultScreenId = tag.getString("DefaultScreenId");
        for (Tag entryTag : tag.getList("Screens", Tag.TAG_COMPOUND)) {
            if (entryTag instanceof CompoundTag compound) {
                ReplayScreenEntry entry = ReplayScreenEntry.load(compound);
                data.screens.put(entry.id(), entry);
            }
        }
        return data;
    }

    public Map<String, ReplayScreenEntry> screens() {
        return Map.copyOf(screens);
    }

    public Optional<ReplayScreenEntry> getScreen(String id) {
        return Optional.ofNullable(screens.get(id));
    }

    public Optional<ReplayScreenEntry> getDefaultScreen() {
        if (defaultScreenId == null || defaultScreenId.isBlank()) {
            return Optional.empty();
        }
        return getScreen(defaultScreenId);
    }

    public String defaultScreenId() {
        return defaultScreenId;
    }

    public void putScreen(ReplayScreenEntry entry, boolean makeDefault) {
        screens.put(entry.id(), entry);
        if (makeDefault || defaultScreenId == null || defaultScreenId.isBlank()) {
            defaultScreenId = entry.id();
        }
        setDirty(true);
    }

    public Optional<ReplayScreenEntry> removeScreen(String id) {
        ReplayScreenEntry removed = screens.remove(id);
        if (id.equals(defaultScreenId)) {
            defaultScreenId = screens.keySet().stream().findFirst().orElse("");
        }
        setDirty(true);
        return Optional.ofNullable(removed);
    }

    public boolean setDefaultScreen(String id) {
        if (!screens.containsKey(id)) {
            return false;
        }
        defaultScreenId = id;
        setDirty(true);
        return true;
    }

    public void updateLastTextDisplay(String id, @Nullable UUID entityId) {
        ReplayScreenEntry entry = screens.get(id);
        if (entry == null) {
            return;
        }
        screens.put(id, entry.withLastTextDisplay(entityId));
        setDirty(true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("DefaultScreenId", defaultScreenId == null ? "" : defaultScreenId);
        ListTag list = new ListTag();
        for (ReplayScreenEntry entry : screens.values()) {
            list.add(entry.save());
        }
        tag.put("Screens", list);
        return tag;
    }

    public record ReplayScreenEntry(
            String id,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int width,
            int height,
            Direction direction,
            @Nullable UUID lastTextDisplay) {

        public ReplayScreenEntry withLastTextDisplay(@Nullable UUID entityId) {
            return new ReplayScreenEntry(id, dimension, origin, width, height, direction, entityId);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Dimension", dimension.location().toString());
            tag.putInt("X", origin.getX());
            tag.putInt("Y", origin.getY());
            tag.putInt("Z", origin.getZ());
            tag.putInt("Width", width);
            tag.putInt("Height", height);
            tag.putString("Direction", direction.getSerializedName());
            if (lastTextDisplay != null) {
                tag.putUUID("LastTextDisplay", lastTextDisplay);
            }
            return tag;
        }

        public static ReplayScreenEntry load(CompoundTag tag) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (dimensionId == null) {
                dimensionId = Level.OVERWORLD.location();
            }
            Direction direction = Direction.byName(tag.getString("Direction"));
            if (direction == null || direction.getAxis().isVertical()) {
                direction = Direction.NORTH;
            }
            UUID lastTextDisplay = tag.hasUUID("LastTextDisplay") ? tag.getUUID("LastTextDisplay") : null;
            return new ReplayScreenEntry(
                    tag.getString("Id"),
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                    Math.max(1, tag.getInt("Width")),
                    Math.max(1, tag.getInt("Height")),
                    direction,
                    lastTextDisplay);
        }
    }
}
