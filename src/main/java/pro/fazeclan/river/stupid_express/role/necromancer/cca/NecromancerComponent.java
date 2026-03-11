package pro.fazeclan.river.stupid_express.role.necromancer.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;

public class NecromancerComponent implements AutoSyncedComponent {

    public static final ComponentKey<NecromancerComponent> KEY = ComponentRegistry.getOrCreate(
            StupidExpress.id("necromancer"),
            NecromancerComponent.class);

    private final Level level;

    private int availableRevives;

    public int getAvailableRevives() {
        return availableRevives;
    }

    public NecromancerComponent(Level level) {
        this.level = level;
    }

    public void sync() {
        KEY.sync(this.level);
    }

    public void reset() {
        this.availableRevives = 0;
        sync();
    }

    public void increaseAvailableRevives() {
        this.availableRevives++;
    }

    public void decreaseAvailableRevives() {
        this.availableRevives--;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.availableRevives = tag.contains("available_revivals") ? tag.getInt("available_revivals") : 0;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("available_revivals", this.availableRevives);
    }
}
