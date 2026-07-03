package pro.fazeclan.river.stupid_express.role.arsonist.cca;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.UUID;

public class DousedPlayerComponent implements ServerTickingComponent, ClientTickingComponent, AutoSyncedComponent {

    public static final ComponentKey<DousedPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            StupidExpress.id("doused"),
            DousedPlayerComponent.class);

    private final Player player;
    private boolean doused = false;

    public int dousedCount = 0;

    // 点燃此玩家的纵火犯 UUID。被点燃后进入燃烧状态，燃烧结束时据此把击杀归属给纵火犯。
    @Nullable
    private UUID burningKiller = null;

    public boolean getDoused() {
        return this.doused;
    }

    public void setDoused(boolean douse) {
        this.doused = douse;
        this.sync();
    }

    @Nullable
    public UUID getBurningKiller() {
        return this.burningKiller;
    }

    public void setBurningKiller(@Nullable UUID killer) {
        this.burningKiller = killer;
    }

    public DousedPlayerComponent(Player player) {
        this.player = player;
        this.doused = false;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void reset() {
        this.doused = false;
        this.dousedCount = 0;
        sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        SREGameWorldComponent gamep = SREGameWorldComponent.KEY.get(player.level());
        if (gamep != null && gamep.isRole(player, SERoles.ARSONIST))
            return true;
        return false;
        // return true;
    }

    @Override
    public void clientTick() {
    }

    @Override
    public void serverTick() {
        // syncDelay++;
        // if (syncDelay >= 200) {
        // syncDelay = 0;
        // sync();
        // }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.doused = tag.contains("doused") && tag.getBoolean("doused");
        this.dousedCount = tag.contains("dousedCount") ? tag.getInt("dousedCount") : 0;
        this.burningKiller = tag.hasUUID("burningKiller") ? tag.getUUID("burningKiller") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("doused", this.doused);
        tag.putInt("dousedCount", this.dousedCount);
        if (this.burningKiller != null) {
            tag.putUUID("burningKiller", this.burningKiller);
        }
    }

}
