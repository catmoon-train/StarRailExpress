package org.agmas.noellesroles.game.roles.Innocent.recaller;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class RecallerPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<RecallerPlayerComponent> KEY = ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "recaller"), RecallerPlayerComponent.class);
    private final Player player;
    public boolean placed = false;
    public double x = 0;
    public double y = 0;
    public double z = 0;
    @Override
    public Player getPlayer() {
        return player;
    }
    @Override
    public void init() {
        this.placed = false;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public RecallerPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public void clientTick() {
    }

    public void serverTick() {

    }

    public void setPosition() {
        x = player.getX();
        y = player.getY();
        z = player.getZ();
        placed = true;
        this.sync();
    }


    public void teleport() {
        double fromX = player.getX();
        double fromY = player.getY();
        double fromZ = player.getZ();

        if (player.level() instanceof ServerLevel serverLevel) {
            ConfigWorldComponent.onPlayerUsedSkill( (ServerPlayer) player);
            playTeleportEffects(serverLevel, fromX, fromY, fromZ);
        }

        player.teleportTo(x,y,z);

        if (player.level() instanceof ServerLevel serverLevel) {
            playTeleportEffects(serverLevel, x, y, z);
        }

        placed = false;
        this.sync();
    }

    private void playTeleportEffects(ServerLevel serverLevel, double centerX, double centerY, double centerZ) {
        double particleY = centerY + 0.9D;

        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2D * i / 16D;
            double offsetX = Math.cos(angle) * 0.8D;
            double offsetZ = Math.sin(angle) * 0.8D;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    centerX + offsetX, particleY, centerZ + offsetZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL,
                centerX, particleY, centerZ,
                10, 0.25D, 0.35D, 0.25D, 0.05D);

        serverLevel.playSound(null, centerX, centerY, centerZ,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }


    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putDouble("x", this.x);
        tag.putDouble("y", this.y);
        tag.putDouble("z", this.z);
        tag.putBoolean("placed", this.placed);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.x = tag.contains("x") ? tag.getDouble("x") : 0;
        this.y = tag.contains("y") ? tag.getDouble("y") : 0;
        this.z = tag.contains("z") ? tag.getDouble("z") : 0;
        this.placed = tag.contains("placed") && tag.getBoolean("placed");
    }

    
    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
