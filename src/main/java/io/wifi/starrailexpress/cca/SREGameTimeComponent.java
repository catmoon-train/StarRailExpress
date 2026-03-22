package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;

public class SREGameTimeComponent implements AutoSyncedComponent, CommonTickingComponent {
    public static final ComponentKey<SREGameTimeComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("time"), SREGameTimeComponent.class);
    public final Level world;
    public int resetTime;
    public int time;

    public SREGameTimeComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void reset() {
        this.setTime(this.resetTime);
    }

    public int getResetTime() {
        return this.resetTime;
    }
    @Override
    public void tick() {
        if (!world.isClientSide){
            if (world.getServer().tickRateManager().isFrozen()){
                return;
            }
        }
        if (!SREGameWorldComponent.KEY.get(this.world).isRunning()) return;
        if (this.time <= 0) return;
        this.time--;
        // 从每400tick增加到每600tick同步（30秒）
        if (this.time % 600 == 0) this.sync();
        
        // 更新计分板上的游戏计时器
        if (this.time % 20 == 0) { // 每秒更新一次计分板
            final var server = this.world.getServer();
            if (server==null)return;
            SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY.get(server.getScoreboard());
            scoreboardComponent.updateGameTimers(this.world);
        }
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

    public void setResetTime(int time) {
        this.resetTime = time;
    }

    public void setTime(int time) {
        this.time = time;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("resetTime", this.resetTime);
        tag.putInt("time", this.time);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.resetTime = tag.getInt("resetTime");
        this.time = tag.getInt("time");
    }
}