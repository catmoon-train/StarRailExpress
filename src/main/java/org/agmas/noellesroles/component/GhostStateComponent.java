package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class GhostStateComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<GhostStateComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("ghost_state"), GhostStateComponent.class);
    private final Player player;

    public boolean isGhost = false;
    public void sync(){
        GhostStateComponent.KEY.sync(player);
    }
    public GhostStateComponent(Player player) {
        this.player = player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.isGhost = false;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isGhost", this.isGhost);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isGhost = tag.getBoolean("isGhost");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isGhost = tag.getBoolean("isGhost");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isGhost", this.isGhost);
    }

    public boolean isGhostState() {
        return this.isGhost;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer serverPlayer){
            if (!serverPlayer.hasEffect(ModEffects.GHOST_STATE)) {
                GhostStateComponent ghostStateComponent = GhostStateComponent.KEY.get(serverPlayer);
                if (ghostStateComponent.isGhostState()) {
                    ghostStateComponent.clear();
                    ghostStateComponent.sync();

                }
            }
        }
    }
}
