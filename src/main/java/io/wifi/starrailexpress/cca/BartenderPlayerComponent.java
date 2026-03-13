package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;

import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class BartenderPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<BartenderPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "bartender"), BartenderPlayerComponent.class);
    private final Player player;
    /**
     * 0: Drink
     * 1: Food
     * ...
     */
    public HashMap<Integer, Integer> glowTicks = new HashMap<>();

    public static ArrayList<String> canSyncedRolePaths = new ArrayList<>();
    private static StarGameWorldComponent gameWorldComponent = null;

    public int getArmor() {
        return armor;
    }

    public int armor = 0;

    public void addArmor() {
        ++this.armor;
        this.sync();
    }

    public void removeArmor() {
        --this.armor;
        this.sync();
    }

    public void removeArmor(int amount) {
        this.armor -= amount;
        this.sync();
    }

    public void reset() {
        this.glowTicks.clear();
        this.armor = 0;
        this.sync_with_all();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        if (player == this.player)
            return true;
        if (gameWorldComponent == null) {
            gameWorldComponent = StarGameWorldComponent.KEY.get(this.player.level());
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                if (canSyncedRolePaths.stream().anyMatch((p) -> p.equals(role.identifier().getPath()))) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public BartenderPlayerComponent(Player player) {
        this.player = player;
        if (gameWorldComponent == null) {
            gameWorldComponent = StarGameWorldComponent.KEY.get(this.player.level());
        }
    }

    public void sync_with_all() {
        for (var p : this.player.getServer().getPlayerList().getPlayers()) {
            KEY.syncWith(p, this.player.asComponentProvider());
        }
        KEY.sync(this.player);
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
        for (var pair : this.glowTicks.entrySet()) {
            if (pair.getValue() >= 2) {
                this.glowTicks.put(pair.getKey(), pair.getValue() - 1);
            }
        }
    }

    public static int tick_ = 0;

    public void serverTick() {
        boolean shouldSync = false;
        for (var pair : this.glowTicks.entrySet()) {
            int glowtick = pair.getValue();
            if (glowtick >= 1) {
                glowtick--;
                this.glowTicks.put(pair.getKey(), glowtick);
                if (glowtick <= 0) {
                    shouldSync = true;
                }
            }
        }
        if (++tick_ % 200 == 0) { // 10s
            shouldSync = true;
        }
        if (shouldSync) {
            this.sync();
        }
    }

    public boolean giveArmor() {
        armor = 1;
        this.sync();
        return true;
    }

    public boolean startGlow() {
        return this.startGlow(0);
    }

    public boolean startGlow(int type) {
        setGlowTicks(GameConstants.getInTicks(0, SREConfig.bartenderGlowDuration), type);
        return true;
    }

    public void setGlowTicks(int ticks, int type) {
        this.glowTicks.put(type, ticks);
        this.sync();
    }

    public void setGlowTicks(int ticks) {
        this.setGlowTicks(ticks, 0);
    }

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (!this.glowTicks.isEmpty()) {
            ListTag targetListTag = new ListTag();
            for (var ent : this.glowTicks.entrySet()) {
                CompoundTag targetTag = new CompoundTag();
                targetTag.putInt("type", ent.getKey());
                targetTag.putInt("time", ent.getValue());
                targetListTag.add(targetTag);
            }
            tag.put("glowTicks", targetListTag);
        }
        if (this.armor > 0)
            tag.putInt("armor", this.armor);
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.glowTicks.clear();
        if (tag.contains("glowTicks", Tag.TAG_LIST)) {
            ListTag targetListTag = tag.getList("glowTicks", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetListTag.size(); i++) {
                CompoundTag targetTag = targetListTag.getCompound(i);
                int type = targetTag.contains("type") ? targetTag.getInt("type") : null;
                int time = targetTag.contains("time") ? targetTag.getInt("time") : null;
                this.glowTicks.put(type, time);
            }
        }
        this.armor = tag.contains("armor") ? tag.getInt("armor") : 0;
    }

    @Override
    public void clear() {
        this.glowTicks.clear();
        this.armor = 0;
        this.sync_with_all();
    }
}
