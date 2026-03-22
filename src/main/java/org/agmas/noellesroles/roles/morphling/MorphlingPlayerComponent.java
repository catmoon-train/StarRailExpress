package org.agmas.noellesroles.roles.morphling;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class MorphlingPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<MorphlingPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "morphling"), MorphlingPlayerComponent.class);
    private final Player player;
    public UUID disguise;
    public int morphTicks = 0;
    public int tickR = 0;

    @Override
    public void init() {
        this.stopMorph();
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return true;
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public MorphlingPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
        if (this.morphTicks != 0) {
            if (this.morphTicks > 0) {
                this.morphTicks--;
            } else {
                this.morphTicks++;
            }
        }
    }

    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRole(this.player, ModRoles.MORPHLING))
            return;
        if (this.morphTicks != 0) {
            ++tickR;
            if (this.morphTicks > 0) {
                if (disguise != null) {
                    if (player.level().getPlayerByUUID(disguise) != null) {
                        // if (((ServerPlayer) player.level().getPlayerByUUID(disguise)).gameMode
                        // .getGameModeForPlayer() == GameType.SPECTATOR) {
                        // stopMorph();
                        // return;
                        // }
                    } else {
                        stopMorph();
                        return;
                    }
                } else {
                    stopMorph();
                    return;
                }

                if (--this.morphTicks == 0) {
                    this.stopMorph();
                    return;
                }
            } else if (this.morphTicks < 0) {
                this.morphTicks++;
                if (this.morphTicks == 0) {
                    KEY.syncWith((ServerPlayer) player, (ComponentProvider) player, this, this);
                    return;
                }
            }

            if (tickR % 200 == 0) {
                KEY.syncWith((ServerPlayer) player, (ComponentProvider) player, this, this);
            }
        }
    }

    public boolean startMorph(UUID id) {
        setMorphTicks(GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().morphlingMorphDuration));
        disguise = id;
        this.sync();
        return true;
    }

    public void stopMorph() {
        this.morphTicks = -GameConstants.getInTicks(0, NoellesRolesConfig.HANDLER.instance().morphlingMorphCooldown);
        this.sync();
    }

    public int getMorphTicks() {
        return this.morphTicks;
    }

    public void setMorphTicks(int ticks) {
        this.morphTicks = ticks;
        this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("morphTicks", this.morphTicks);
        if (disguise != null)
            tag.putUUID("disguise", this.disguise);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.morphTicks = tag.contains("morphTicks") ? tag.getInt("morphTicks") : 0;
        this.disguise = tag.contains("disguise") ? tag.getUUID("disguise") : player.getUUID();
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
