package org.agmas.noellesroles.roles.coroner;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import io.wifi.starrailexpress.api.RoleComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class BodyDeathReasonComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<BodyDeathReasonComponent> KEY = ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "body_death_reason"),
            BodyDeathReasonComponent.class);
    public ResourceLocation playerRole = TMMRoles.CIVILIAN.identifier();
    public boolean vultured = false;
    public PlayerBodyEntity playerBodyEntity;

    @Override
    public void init() {
        this.sync();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    @Override
    public void clear() {
        this.init();
    }

    public BodyDeathReasonComponent(PlayerBodyEntity playerBodyEntity) {
        this.playerBodyEntity = playerBodyEntity;
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    public void sync() {
        KEY.sync(this.playerBodyEntity);
    }

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putString("playerRole", playerRole.toString());
        tag.putBoolean("vultured", vultured);
    }

    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.playerRole = ResourceLocation.parse(tag.getString("playerRole"));
        this.vultured = tag.getBoolean("vultured");
    }

    @Override
    public void serverTick() {

    }
}
