package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ParticipationComponent implements AutoSyncedComponent {
    public static final ComponentKey<ParticipationComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("participation"), ParticipationComponent.class);

    private final Level world;
    private final Set<UUID> optedOutPlayers = new HashSet<>();

    public ParticipationComponent(Level world) {
        this.world = world;
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public boolean isParticipating(Player player) {
        return player != null && isParticipating(player.getUUID());
    }

    public boolean isParticipating(UUID playerId) {
        return playerId != null && !this.optedOutPlayers.contains(playerId);
    }

    public void setParticipating(UUID playerId, boolean participating) {
        if (playerId == null) {
            return;
        }
        if (participating) {
            this.optedOutPlayers.remove(playerId);
        } else {
            this.optedOutPlayers.add(playerId);
        }
        this.sync();
    }

    public boolean toggleParticipating(Player player) {
        boolean participating = !isParticipating(player);
        setParticipating(player.getUUID(), participating);
        return participating;
    }

    public int getOptedOutOnlineCount() {
        if (this.world == null) {
            return 0;
        }
        return (int) this.world.players().stream()
                .filter(player -> !isParticipating(player))
                .count();
    }

    public int getParticipatingOnlineCount() {
        if (this.world == null) {
            return 0;
        }
        return (int) this.world.players().stream()
                .filter(this::isParticipating)
                .count();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        return true;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.optedOutPlayers.clear();
        ListTag players = tag.getList("OptedOutPlayers", Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) {
            try {
                this.optedOutPlayers.add(UUID.fromString(players.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag players = new ListTag();
        for (UUID playerId : this.optedOutPlayers) {
            players.add(StringTag.valueOf(playerId.toString()));
        }
        tag.put("OptedOutPlayers", players);
    }
}
