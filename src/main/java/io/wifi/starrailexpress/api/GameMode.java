package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public abstract class GameMode {
    public final ResourceLocation identifier;
    public final int defaultStartTime;
    public final int minPlayerCount;

    /**
     * @param identifier the game mode identifier
     * @param defaultStartTime the default time at which the timer will be set at the start of the game mode, in minutes
     * @param minPlayerCount the minimum amount of players required to start the game mode
     */
    public GameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        this.identifier = identifier;
        this.defaultStartTime = defaultStartTime;
        this.minPlayerCount = minPlayerCount;
    }

    public void readFromNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

    }

    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {

    }

    public void tickCommonGameLoop() {}

    public void tickClientGameLoop() {}

    public abstract void tickServerGameLoop(ServerLevel serverWorld, GameWorldComponent gameWorldComponent);

    public abstract void initializeGame(ServerLevel serverWorld, GameWorldComponent gameWorldComponent, List<ServerPlayer> players);

    public void finalizeGame(ServerLevel serverWorld, GameWorldComponent gameWorldComponent) {

    }
}