package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class SREArmorPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREArmorPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "armor"), SREArmorPlayerComponent.class);
    private final Player player;
    private static SREGameWorldComponent gameWorldComponent = null;

    public static ArrayList<String> canSyncedRolePaths = new ArrayList<>();
    public int armor = 0;

    public int getArmor() {
        return armor;
    }

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

    public void init() {

        this.armor = 0;
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        if (player == this.player)
            return true;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
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
        return false;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public SREArmorPlayerComponent(Player player) {
        this.player = player;
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
        
    }

    public static int tick_ = 0;

    public void serverTick() {
    }

    public boolean giveArmor() {
        armor = 1;
        this.sync();
        return true;
    }


    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.armor > 0)
            tag.putInt("armor", this.armor);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.armor = tag.contains("armor") ? tag.getInt("armor") : 0;
    }

    @Override
    public void clear() {
        this.armor = 0;
        this.sync();
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
