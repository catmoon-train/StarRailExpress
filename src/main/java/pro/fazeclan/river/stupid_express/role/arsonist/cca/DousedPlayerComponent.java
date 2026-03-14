package pro.fazeclan.river.stupid_express.role.arsonist.cca;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SERoles;

public class DousedPlayerComponent implements ServerTickingComponent, ClientTickingComponent, AutoSyncedComponent {

    public static final ComponentKey<DousedPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            StupidExpress.id("doused"),
            DousedPlayerComponent.class);

    private final Player player;
    private boolean doused = false;

    // Only save on server-side
    public int dousedCount = 0;

    public boolean getDoused() {
        return this.doused;
    }

    public void setDoused(boolean douse) {
        this.doused = douse;
        this.sync();
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
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("doused", this.doused);
    }

}
