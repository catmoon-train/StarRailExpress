package org.agmas.noellesroles.roles.vulture;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class VulturePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<VulturePlayerComponent> KEY = ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "vulture"), VulturePlayerComponent.class);
    private final Player player;
    public int bodiesEaten = 0;
    public int bodiesRequired = 0;

    @Override
    public Player getPlayer() {
        return player;
    }
    @Override
    public void init() {

        this.sync();
    }

    @Override
    public void clear() {
        this.bodiesEaten = 0;
        this.bodiesRequired = 0;
        this.sync();
    }

    public VulturePlayerComponent(Player player) {
        this.player = player;
        bodiesEaten = 0;
        bodiesRequired = 0;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
    }

    public void serverTick() {

    }


    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("bodiesEaten", this.bodiesEaten);
        tag.putInt("bodiesRequired", this.bodiesRequired);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.bodiesEaten = tag.contains("bodiesEaten") ? tag.getInt("bodiesEaten") : 0;
        this.bodiesRequired = tag.contains("bodiesRequired") ? tag.getInt("bodiesRequired") : 0;
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
