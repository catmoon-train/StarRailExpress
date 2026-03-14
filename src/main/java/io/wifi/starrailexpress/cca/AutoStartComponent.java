package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

public class AutoStartComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final ComponentKey<AutoStartComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("autostart"),
            AutoStartComponent.class);
    public final Level world;
    public int startTime;
    public int time;

    public AutoStartComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void reset() {
        this.setTime(this.startTime);
    }

    @Override
    public void tick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.world);
        if (gameWorldComponent.isRunning())
            return;

        if (this.startTime <= 0 && this.time <= 0)
            return;

        if (GameUtils.getReadyPlayerCount(world) >= gameWorldComponent.getGameMode().minPlayerCount) {
            if (this.time-- <= 0 && this.world instanceof ServerLevel serverWorld) {
                if (gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.INACTIVE) {
                    GameMode gameMode = SREGameModes.MURDER;
                    GameUtils.startGame(serverWorld, gameMode,
                            GameConstants.getInTicks(gameMode.defaultStartTime, 0));
                    return;
                }
            }

            if (this.getTime() % 40 == 0) {
                this.sync();
            }
        } else {
            if (this.world.getGameTime() % 20 == 0) {
                // this.setTime(this.startTime);
                this.time = this.startTime;
            }
            if (this.world.getGameTime() % 40 == 0) {
                this.sync();
            }
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.world);
        if (gameWorldComponent.isRunning()) {
            return false;
        }
        if (this.startTime <= 0 && this.time <= 0)
            return false;
        return true;
    }

    public boolean isAutoStartActive() {
        return startTime > 0;
    }

    public boolean hasTime() {
        return this.time > 0;
    }

    public int getTime() {
        return this.time;
    }

    public void addTime(int time) {
        this.setTime(this.time + time);
    }

    public void setStartTime(int time) {
        this.startTime = time;
    }

    public void setTime(int time) {
        this.time = time;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("startTime", this.startTime);
        tag.putInt("time", this.time);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.startTime = tag.getInt("startTime");
        this.time = tag.getInt("time");
    }
}