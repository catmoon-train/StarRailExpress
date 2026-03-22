package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;

public class DynamicShopComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<DynamicShopComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("dynamic_shop"), DynamicShopComponent.class);
    private final Player player;
    private Map<ResourceLocation,Integer> coins;

    public DynamicShopComponent(Player player) {
        this.player = player;
        coins = new HashMap<>() ;
    }

    @Override
    public Player getPlayer() {
        return player;

    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        coins = new HashMap<>() ;
    }

    @Override
    public void clear(){
        init();
    }
    @Override
    public void clientTick() {

    }

    @Override
    public void serverTick() {

    }

    @Override
    public void readFromSyncNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        coins.clear();
        CompoundTag coinsTag = compoundTag.getCompound("coins");
        for (String key : coinsTag.getAllKeys()) {
            ResourceLocation resourceLocation = ResourceLocation.tryParse(key);
            if (resourceLocation != null) {
                int value = coinsTag.getInt(key);
                coins.put(resourceLocation, value);
            }
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        CompoundTag coinsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : coins.entrySet()) {
            coinsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        compoundTag.put("coins", coinsTag);
    }
    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
