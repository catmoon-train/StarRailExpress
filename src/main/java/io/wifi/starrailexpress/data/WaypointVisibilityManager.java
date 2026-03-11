package io.wifi.starrailexpress.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class WaypointVisibilityManager extends SavedData {
    private static final String DATA_NAME = "starrailexpress_waypoint_visibility";
    private boolean waypointsVisible = false; // 默认隐藏路径点

    public WaypointVisibilityManager() {}

    public WaypointVisibilityManager(boolean waypointsVisible) {
        this.waypointsVisible = waypointsVisible;
    }

    public static WaypointVisibilityManager get(ServerLevel level) {
        return get(level.getServer());
    }

    public static WaypointVisibilityManager get(MinecraftServer server) {
        ServerLevel level = server.getLevel(Level.OVERWORLD); // 使用主世界存储
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
            new SavedData.Factory<WaypointVisibilityManager>(WaypointVisibilityManager::new,
                    (a,b)->load(a),
                                  null),
            DATA_NAME
        );
    }

    public static WaypointVisibilityManager load(CompoundTag tag) {
        boolean isVisible = tag.getBoolean("waypointsVisible");
        return new WaypointVisibilityManager(isVisible);
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        compoundTag.putBoolean("waypointsVisible", waypointsVisible);
        return compoundTag;
    }




    public boolean getWaypointsVisibility() {
        return waypointsVisible;
    }

    public void setWaypointsVisibility(boolean visible) {
        this.waypointsVisible = visible;
        setDirty(true); // 标记为脏数据以便保存
    }

    public void toggleWaypointsVisibility() {
        this.waypointsVisible = !this.waypointsVisible;
        setDirty(true); // 标记为脏数据以便保存
    }
}