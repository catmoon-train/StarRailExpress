package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class LocksmithInspirationComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<LocksmithInspirationComponent> KEY = ModComponents.LOCKSMITH_INSPIRATION;

    public static final int MAX_POINTS = 18;
    public static final int OBSERVE_TICKS_REQUIRED = 20 * 15;

    private final Player player;
    private int inspirationPoints = 0;
    private int observingDoorTicks = 0;

    public LocksmithInspirationComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.inspirationPoints = 0;
        this.observingDoorTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
    }

    public int getInspirationPoints() {
        return inspirationPoints;
    }

    public int getObservingDoorTicks() {
        return observingDoorTicks;
    }

    public void setObservingDoorTicks(int ticks) {
        int clamped = Math.max(0, ticks);
        if (this.observingDoorTicks != clamped) {
            this.observingDoorTicks = clamped;
            this.sync();
        }
    }

    public int incrementObservingDoorTicks() {
        this.observingDoorTicks++;
        return this.observingDoorTicks;
    }

    public boolean addInspiration(int amount) {
        int next = Math.min(MAX_POINTS, Math.max(0, this.inspirationPoints + amount));
        if (next == this.inspirationPoints) {
            return false;
        }
        this.inspirationPoints = next;
        this.sync();
        return true;
    }

    public boolean consumeInspiration(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.inspirationPoints < amount) {
            return false;
        }
        this.inspirationPoints -= amount;
        this.sync();
        return true;
    }

    public void sync() {
        ModComponents.LOCKSMITH_INSPIRATION.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("inspirationPoints", this.inspirationPoints);
        tag.putInt("observingDoorTicks", this.observingDoorTicks);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("inspirationPoints", this.inspirationPoints);
        tag.putInt("observingDoorTicks", this.observingDoorTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inspirationPoints = Math.max(0, Math.min(MAX_POINTS, tag.getInt("inspirationPoints")));
        this.observingDoorTicks = Math.max(0, tag.getInt("observingDoorTicks"));
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inspirationPoints = Math.max(0, Math.min(MAX_POINTS, tag.getInt("inspirationPoints")));
        this.observingDoorTicks = Math.max(0, tag.getInt("observingDoorTicks"));
    }
}