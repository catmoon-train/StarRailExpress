package org.agmas.noellesroles.roles.voodoo;

import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

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

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putUUID("target", player.getUUID());
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : player.getUUID();
    }
}
