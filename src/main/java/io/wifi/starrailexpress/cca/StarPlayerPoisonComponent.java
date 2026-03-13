package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;

public class StarPlayerPoisonComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<StarPlayerPoisonComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("poison"),
            StarPlayerPoisonComponent.class);
    public static final Tuple<Integer, Integer> clampTime = new Tuple<>(800, 1400);
    private final Player player;
    public int poisonTicks = -1;
    private int initialPoisonTicks = 0;
    private int poisonPulseCooldown = 0;
    public float pulseProgress = 0f;
    public boolean pulsing = false;
    public UUID poisoner;
    private static StarGameWorldComponent gameWorldComponent = null;
    public static ArrayList<String> canSyncedRolePaths = new ArrayList<>();

    public StarPlayerPoisonComponent(Player player) {
        this.player = player;
        gameWorldComponent = StarGameWorldComponent.KEY.get(this.player.level());
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
                return canSyncedRolePaths.contains(role.identifier().getPath());
            }
        }
        return true;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public void reset() {
        this.poisonTicks = -1;
        this.poisonPulseCooldown = 0;
        this.initialPoisonTicks = 0;
        this.pulseProgress = 0f;
        this.pulsing = false;
        this.sync_with_all();
    }

    @Override
    public void clear() {
        this.reset();
    }

    public void sync_with_all() {
        for (var p : this.player.getServer().getPlayerList().getPlayers()) {
            KEY.syncWith(p, this.player.asComponentProvider());
        }
        KEY.sync(this.player);
    }

    @Override
    public void clientTick() {
        if (this.poisonTicks > 0)
            this.poisonTicks--;
        if (this.poisonTicks > 0) {
            int ticksSinceStart = this.initialPoisonTicks - this.poisonTicks;

            if (ticksSinceStart < 100)
                return;

            int minCooldown = 10;
            int maxCooldown = 60;
            int dynamicCooldown = minCooldown
                    + (int) ((maxCooldown - minCooldown) * ((float) this.poisonTicks / clampTime.getB()));

            if (this.poisonPulseCooldown <= 0) {
                this.poisonPulseCooldown = dynamicCooldown;

                this.pulsing = true;

                float minVolume = 0.5f;
                float maxVolume = 1f;
                float volume = minVolume
                        + (maxVolume - minVolume) * (1f - ((float) this.poisonTicks / clampTime.getB()));

                this.player.playNotifySound(
                        SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS,
                        volume,
                        1f);
            } else {
                this.poisonPulseCooldown--;
            }
        } else {
            this.poisonPulseCooldown = 0;
        }
    }

    @Override
    public void serverTick() {
        if (this.poisonTicks > 0) {
            this.poisonTicks--;
            if (this.poisonTicks == 0) {
                this.poisonTicks = -1;
                GameFunctions.killPlayer(this.player, true,
                        this.poisoner == null ? null : this.player.level().getPlayerByUUID(this.poisoner),
                        GameConstants.DeathReasons.POISON);
                this.poisoner = null;
                this.sync();
            }
        }
    }

    public void setPoisonTicks(int ticks, UUID poisoner) {
        this.poisoner = poisoner;
        this.poisonTicks = ticks;
        if (this.initialPoisonTicks == 0)
            this.initialPoisonTicks = ticks;
        this.sync();
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.poisoner != null)
            tag.putUUID("poisoner", this.poisoner);
        if (this.poisonTicks >= 0)
            tag.putInt("poisonTicks", this.poisonTicks);
        if (this.initialPoisonTicks >= 0)
        tag.putInt("initialPoisonTicks", this.initialPoisonTicks);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.poisoner = tag.contains("poisoner") ? tag.getUUID("poisoner") : null;
        this.poisonTicks = tag.contains("poisonTicks") ? tag.getInt("poisonTicks") : -1;
        this.initialPoisonTicks = tag.contains("initialPoisonTicks") ? tag.getInt("initialPoisonTicks") : 0;
    }
}