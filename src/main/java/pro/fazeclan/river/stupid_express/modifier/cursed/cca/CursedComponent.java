package pro.fazeclan.river.stupid_express.modifier.cursed.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.UUID;

public class CursedComponent implements RoleComponent {

    public static final ComponentKey<CursedComponent> KEY =
            ComponentRegistry.getOrCreate(StupidExpress.id("cursed"), CursedComponent.class);

    private final Player player;

    private UUID cursed;

    public CursedComponent(Player player) {
        this.player = player;
    }

    public UUID getCursed() {
        return this.cursed;
    }

    public void setCursed(UUID uuid) {
        this.cursed = uuid;
        sync();
    }

    public boolean isCursed() {
        return this.cursed != null && this.cursed.equals(player.getUUID());
    }

    public void init() {
        this.cursed = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cursed = tag.contains("cursed") ? tag.getUUID("cursed") : null;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.cursed != null) {
            tag.putUUID("cursed", this.cursed);
        }
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}