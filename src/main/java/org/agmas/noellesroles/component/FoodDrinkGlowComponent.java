package org.agmas.noellesroles.component;

import java.util.HashMap;
import java.util.Set;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

public class FoodDrinkGlowComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    //
    public static final ComponentKey<FoodDrinkGlowComponent> KEY = ComponentRegistry.getOrCreate(
            Noellesroles.id("food_drink"), FoodDrinkGlowComponent.class);
    private final Player player;
    // 玩家 | 类型
    public HashMap<String, Integer> pendingSenders = new HashMap<>();

    /**
     * 0: Drink
     * 1: Food
     * ...
     */
    public static void playerDrink(Player p) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(p.level());
        if (!gameWorldComponent.isRunning())
            return;
        for (var p2 : p.level().players()) {
            if (gameWorldComponent.isRole(p2, ModRoles.BARTENDER)) {
                FoodDrinkGlowComponent.KEY.get(p2).startGlow(p, 0);
                break;
            }
        }
    }

    public static void playerEat(Player p) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(p.level());
        if (!gameWorldComponent.isRunning())
            return;
        for (var p2 : p.level().players()) {
            if (gameWorldComponent.isRole(p2, ModRoles.CHEF)) {
                FoodDrinkGlowComponent.KEY.get(p2).startGlow(p, 1);
                break;
            }
        }
    }

    public HashMap<String, HashMap<Integer, Integer>> glowTicks = new HashMap<>();

    public FoodDrinkGlowComponent(Player player) {
        this.player = player;
    }

    @Override
    public void clientTick() {
        for (var pair : this.glowTicks.entrySet()) {
            for (var pair2 : pair.getValue().entrySet()) {
                if (pair2.getValue() >= 1) {
                    pair.getValue().put(pair2.getKey(), pair2.getValue() - 1);
                }
            }
        }
    }

    @Override
    public void serverTick() {
        // if (this.player.level().getGameTime() % 20 == 0 &&
        // !this.pendingSenders.isEmpty()) { // 每秒处理一次避免丢包
        // sync();
        // this.pendingSenders.clear();
        // }
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void init() {
        this.glowTicks.clear();
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * Server-side call
     * 
     * @param player 玩家
     * @param type   类型
     * @return
     */
    public boolean startGlow(Player player, int type) {
        return this.startGlow(player, type, true);
    }

    public boolean startGlow(Player player, int type, boolean sync) {
        this.pendingSenders.put(player.getScoreboardName(), type);
        if (sync)
            this.sync();
        return true;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (!this.pendingSenders.isEmpty()) {
            CompoundTag tag2 = new CompoundTag();
            for (var entry : this.pendingSenders.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank())
                    tag2.putInt(entry.getKey(), entry.getValue());
            }
            this.pendingSenders.clear();
            tag.put("a", tag2);
            tag.putInt("t", GameConstants.getInTicks(0, SREConfig.instance().bartenderGlowDuration));
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
        if (tag.contains("a") && tag.contains("t")) {
            int time = tag.getInt("t");
            var tag2 = tag.getCompound("a");
            Set<String> keys = tag2.getAllKeys();
            for (var key : keys) {
                if (tag2.contains(key, Tag.TAG_INT)) {
                    int type = tag2.getInt(key);
                    this.glowTicks.putIfAbsent(key, new HashMap<>());
                    var map = this.glowTicks.get(key);
                    map.put(type, time);
                }
            }
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, Provider registryLookup) {
    }
}
