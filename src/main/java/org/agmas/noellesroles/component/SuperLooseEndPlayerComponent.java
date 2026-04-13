package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SuperLooseEndPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<SuperLooseEndPlayerComponent> KEY = ModComponents.SUPER_LOOSE_END;
    private final Player player;
    public SuperLooseEndPlayerComponent(Player player) {
        this.player = player;
    }
    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {

    }

    @Override
    public void clear() {

    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void serverTick() {

    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {

    }
}
