package org.agmas.noellesroles.game.roles.killer.skincrawler;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class SkincrawlerPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<SkincrawlerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "skincrawler"),
            SkincrawlerPlayerComponent.class);

    public static final int STEAL_COOLDOWN = 60 * 20;

    private final Player player;
    public int stealCooldown;
    public UUID stolenSkin;

    public SkincrawlerPlayerComponent(Player player) { this.player = player; }
    @Override public Player getPlayer() { return player; }
    @Override public void init() { stealCooldown = 0; stolenSkin = null; sync(); }
    @Override public void clear() { init(); }
    public void sync() { KEY.sync(player); }

    public boolean isActive() {
        if (player == null || player.level().isClientSide()) return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SKINCRAWLER);
    }

    @Override
    public void serverTick() {
        if (!isActive()) return;
        if (stealCooldown > 0) { stealCooldown--; sync(); }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("stealCooldown", stealCooldown);
        if (stolenSkin != null) tag.putString("stolenSkin", stolenSkin.toString());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        stealCooldown = tag.getInt("stealCooldown");
        stolenSkin = tag.contains("stolenSkin") ? UUID.fromString(tag.getString("stolenSkin")) : null;
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
}
