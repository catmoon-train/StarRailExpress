package org.agmas.noellesroles.roles.fortuneteller;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class FortunetellerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<FortunetellerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fortuneteller"),
            FortunetellerPlayerComponent.class);
    private final Player player;
    public ArrayList<ProtectedInfo> protectedPlayers = new ArrayList<>();
    /** 过期时间 */
    public static final int MAX_PROTECT_TIME = 120 * 20;

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.protectedPlayers.clear();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public FortunetellerPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
        for (var ppi : protectedPlayers) {
            if (ppi.time > 0)
                ppi.time--;
        }
    }

    public void protectPlayer(Player target) {
        UUID puid = target.getUUID();
        protectedPlayers.add(new ProtectedInfo(puid, MAX_PROTECT_TIME));
        player.displayClientMessage(Component.translatable("message.fortuneteller.protected", target.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(
                Component.translatable("message.fortuneteller.been_protected", player.getDisplayName())
                        .withStyle(ChatFormatting.GOLD),
                true);
        this.sync();
    }

    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.FORTUNETELLER)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        boolean shouldSync = false;
        boolean shouldDelete = false;
        if (player.level().getGameTime() % 200 == 0)
            shouldSync = true;
        for (var ppi : protectedPlayers) {
            ppi.time--;
            if (ppi.time <= 0) {
                shouldDelete = true;
                shouldSync = true;
            }
        }
        if (player.level().getGameTime() % 40 == 0 || shouldDelete) {
            protectedPlayers.removeIf((ppi) -> {
                if ((this.player.level().getPlayerByUUID(ppi.player)) == null) {
                    return true;
                }
                return ppi.time <= 0;
            });
        }
        if (shouldSync)
            this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag targetListTag = new ListTag();
        for (ProtectedInfo targetInfo : protectedPlayers) {
            CompoundTag targetTag = new CompoundTag();
            if (targetInfo.player != null) {
                targetTag.putUUID("player", targetInfo.player);
            }
            if (targetInfo.time >= 0) {
                targetTag.putInt("time", targetInfo.time);
            }
            targetListTag.add(targetTag);
        }
        tag.put("targetList", targetListTag);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        protectedPlayers.clear();
        if (tag.contains("targetList")) {
            ListTag targetListTag = tag.getList("targetList", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetListTag.size(); i++) {
                CompoundTag targetTag = targetListTag.getCompound(i);
                UUID targetPlayer = targetTag.contains("player") ? targetTag.getUUID("player") : null;
                int time = targetTag.contains("time") ? targetTag.getInt("time") : null;
                protectedPlayers.add(new ProtectedInfo(targetPlayer, time));
            }
        }
    }

    public static class ProtectedInfo {
        public UUID player;
        public int time;

        public ProtectedInfo(UUID player, int time) {
            this.player = player;
            this.time = time;
        }
    }

    public boolean hasProtected(Player player2) {
        return this.protectedPlayers.stream().anyMatch((p) -> {
            return player2.getUUID().equals(p.player);
        });
    }

    public boolean triggerProtect(Player player2) {
        if (hasProtected(player2)) {
            this.protectedPlayers.removeIf((p) -> {
                return player2.getUUID().equals(p.player);
            });
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            player2.displayClientMessage(Component.translatable("message.fortuneteller.protection_active_self")
                    .withStyle(ChatFormatting.AQUA), true);
            player.displayClientMessage(
                    Component.translatable("message.fortuneteller.protection_active_role", player2.getDisplayName())
                            .withStyle(ChatFormatting.AQUA),
                    true);
            player2.teleportTo(x, y, z);
            return true;
        }
        return false;
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}