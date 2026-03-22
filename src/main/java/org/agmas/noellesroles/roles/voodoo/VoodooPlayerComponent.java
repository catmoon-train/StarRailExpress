package org.agmas.noellesroles.roles.voodoo;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.UUID;

public class VoodooPlayerComponent implements RoleComponent {
    public static final ComponentKey<VoodooPlayerComponent> KEY = ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "voodoo"), VoodooPlayerComponent.class);
    private final Player player;
    public UUID target;
    @Override
    public Player getPlayer() {
        return player;
    }
    @Override
    public void init() {
        this.target = player.getUUID();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public VoodooPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }


    public void setTarget(UUID target) {
        this.target = target;
        this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putUUID("target", player.getUUID());
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : player.getUUID();
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
