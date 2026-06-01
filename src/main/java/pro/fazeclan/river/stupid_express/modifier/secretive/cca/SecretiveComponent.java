package pro.fazeclan.river.stupid_express.modifier.secretive.cca;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.Objects;
import java.util.UUID;

public class SecretiveComponent implements AutoSyncedComponent {

    public static final ComponentKey<SecretiveComponent> KEY =
            ComponentRegistry.getOrCreate(StupidExpress.id("secretive"), SecretiveComponent.class);

    private final Player player;

    private UUID secretive;

    public SecretiveComponent(Player player) {
        this.player = player;
    }

    public UUID getSecretive() {
        return this.secretive;
    }

    public void setSecretive(UUID uuid) {
        if (Objects.equals(this.secretive, uuid)) {
            return;
        }
        this.secretive = uuid;
        sync();
    }

    public boolean isSecretive() {
        return this.secretive != null && this.secretive.equals(player.getUUID());
    }

    public void reset() {
        if (this.secretive == null) {
            return;
        }
        this.secretive = null;
        sync();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return recipient == this.player;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.secretive = tag.contains("secretive") ? tag.getUUID("secretive") : null;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.secretive != null) {
            tag.putUUID("secretive", this.secretive);
        }
    }
}
