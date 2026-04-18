package org.agmas.noellesroles.game.roles.neutral.panda;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;

public class PandaComponent implements RoleComponent, ClientTickingComponent {
    public static final ComponentKey<PandaComponent> KEY = ModComponents.panda;
    public Player player;
    public boolean isPanda;
    @Override
    public Player getPlayer() {
        return player;
    }
    public PandaComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        isPanda = false;

    }

    @Override
    public void clear() {
        if (isPanda) {
            isPanda = false;
            sync();
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isPanda", isPanda);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        isPanda = tag.contains("isPanda") && tag.getBoolean("isPanda");
        }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        isPanda = tag.contains("isPanda") && tag.getBoolean("isPanda");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isPanda", isPanda);
    }

    @Override
    public void clientTick() {

    }

    public void sync() {
        KEY.sync(player);
    }
}
