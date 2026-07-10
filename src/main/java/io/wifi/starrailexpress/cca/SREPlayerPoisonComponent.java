package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.ArrayList;
import java.util.UUID;

public class SREPlayerPoisonComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<SREPlayerPoisonComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("poison"),
            SREPlayerPoisonComponent.class);
    public static final Tuple<Integer, Integer> clampTime = new Tuple<>(800, 1400);
    private final Player player;
    public int poisonTicks = -1;
    public boolean fakePoison = false;
    private int initialPoisonTicks = 0;
    private int poisonPulseCooldown = 0;
    public float pulseProgress = 0f;
    public boolean pulsing = false;
    public UUID poisoner;
    private SREGameWorldComponent gameWorldComponent = null;
    public static ArrayList<String> canSyncedRolePaths = new ArrayList<>();

    public int getPoisonTicks() {
        return this.poisonTicks;
    }

    public SREPlayerPoisonComponent(Player player) {
        this.player = player;
        gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
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
                return canSyncedRolePaths.contains(role.identifier().getPath());
            }
        }
        return false;
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
        this.poisonTicks = -1;
        this.fakePoison = false;
        this.poisonPulseCooldown = 0;
        this.initialPoisonTicks = 0;
        this.pulseProgress = 0f;
        this.pulsing = false;
        this.sync_with_all();
    }

    @Override
    public void clear() {
        this.init();
    }

    public void sync_with_all() {
        for (var p : this.player.getServer().getPlayerList().getPlayers()) {
            KEY.syncWith(p, this.player.asComponentProvider());
        }
        KEY.sync(this.player);
    }

    public boolean checkIsGameRunning() {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        }
        return gameWorldComponent.gameStatus.equals(SREGameWorldComponent.GameStatus.ACTIVE);
    }

    @Override
    public void clientTick() {
        if (!checkIsGameRunning()) {
            this.poisonTicks = 0;
            this.poisonPulseCooldown = 0;
            this.poisoner = null;
            return;
        }
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
                if (this.fakePoison) {
                    this.fakePoison = false;
                    this.poisoner = null;
                    this.sync();
                    return;
                }
                GameUtils.killPlayer(this.player, true,
                        this.poisoner == null ? null : this.player.level().getPlayerByUUID(this.poisoner),
                        GameConstants.DeathReasons.POISON);
                this.poisoner = null;
                this.sync();
                
                // 清除感染状态（中毒致死时清除感染）
                InfectedPlayerComponent infectedComponent = org.agmas.noellesroles.component.ModComponents.INFECTED.get(this.player);
                if (infectedComponent.infectedTicks > 0) {
                    infectedComponent.cure();
                }
            }
        }
    }

    public void setPoisonTicks(int ticks, UUID poisoner) {
        this.poisoner = poisoner;
        this.poisonTicks = ticks;
        this.fakePoison = false;
        if (this.initialPoisonTicks == 0)
            this.initialPoisonTicks = ticks;
        this.sync();
    }

    public void setFakePoisonTicks(int ticks, UUID poisoner) {
        this.poisoner = poisoner;
        this.poisonTicks = ticks;
        this.fakePoison = true;
        if (this.initialPoisonTicks == 0)
            this.initialPoisonTicks = ticks;
        this.sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.poisoner != null) {
            tag.putUUID("poisoner", this.poisoner);
            if (this.poisonTicks >= 0)
                tag.putInt("poisonTicks", this.poisonTicks);
            if (this.initialPoisonTicks >= 0)
                tag.putInt("initialPoisonTicks", this.initialPoisonTicks);
        }
        tag.putBoolean("fakePoison", this.fakePoison);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.poisoner = tag.contains("poisoner") ? tag.getUUID("poisoner") : null;
        this.poisonTicks = tag.contains("poisonTicks") ? tag.getInt("poisonTicks") : -1;
        this.initialPoisonTicks = tag.contains("initialPoisonTicks") ? tag.getInt("initialPoisonTicks") : 0;
        this.fakePoison = tag.contains("fakePoison") ? tag.getBoolean("fakePoison") : false;
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}