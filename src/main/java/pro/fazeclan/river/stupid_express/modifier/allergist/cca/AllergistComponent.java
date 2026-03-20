package pro.fazeclan.river.stupid_express.modifier.allergist.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import io.wifi.starrailexpress.api.RoleComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;

public class AllergistComponent implements RoleComponent {

    public static final ComponentKey<AllergistComponent> KEY = ComponentRegistry
            .getOrCreate(StupidExpress.id("allergist"), AllergistComponent.class);

    private final Player player;

    private UUID allergist;

    public AllergistComponent(Player player) {
        this.player = player;
    }

    public UUID getAllergist() {
        return this.allergist;
    }

    public void setAllergist(UUID uuid) {
        this.allergist = uuid;
        sync();
    }

    public boolean isAllergist() {
        return this.allergist != null
                && !this.allergist.equals(UUID.fromString("e1e89fbb-3beb-492a-b1be-46a4ce19c9d1"));
    }

    public void init() {
        this.allergist = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.allergist = tag.contains("allergist") ? tag.getUUID("allergist") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putUUID("allergist",
                this.allergist != null ? this.allergist : UUID.fromString("e1e89fbb-3beb-492a-b1be-46a4ce19c9d1"));
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }
}
