package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class DefibrillatorComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    private final Player player;
    public long protectionExpiry = 0;
    public boolean isDead = false;
    public long resurrectionTime = 0;
    public UUID corpseEntityId = null;
    public Vec3 deathPos = null;

    public DefibrillatorComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    public void setProtection(long durationTicks) {
        this.protectionExpiry = player.level().getGameTime() + durationTicks;
        ModComponents.DEFIBRILLATOR.sync(player);
    }

    public boolean hasProtection() {
        return player.level().getGameTime() < protectionExpiry;
    }

    public void triggerDeath(long resurrectionDelayTicks, UUID corpseId, Vec3 pos) {
        this.isDead = true;
        this.resurrectionTime = player.level().getGameTime() + resurrectionDelayTicks;
        this.corpseEntityId = corpseId;
        this.deathPos = pos;
        if (player instanceof ServerPlayer sp) {
            DeathPenaltyComponent.KEY.get(sp).setPenaltyWithPositionLimit(-1, pos, true);
        }
        ModComponents.DEFIBRILLATOR.sync(player);
    }

    public static PlayerBodyEntity findPlayerBodyEntity(ServerPlayer serverPlayer) {
        // 寻找尸体
        if (serverPlayer.serverLevel() instanceof ServerLevel slevel) {
            var entities = slevel.getAllEntities();
            for (var bentity : entities) {
                if (bentity instanceof PlayerBodyEntity body) {
                    if (body.getPlayerUuid().equals(serverPlayer.getUUID())) {
                        return body;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void init() {
        this.protectionExpiry = 0;
        this.isDead = false;
        this.resurrectionTime = 0;
        this.corpseEntityId = null;
        this.deathPos = null;
        this.player.removeEffect(ModEffects.MOVE_BANED);
        this.player.removeEffect(ModEffects.USED_BANED);
        ModComponents.DEFIBRILLATOR.sync(player);
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.protectionExpiry = tag.getLong("protectionExpiry");
        this.isDead = tag.getBoolean("isDead");
        this.resurrectionTime = tag.getLong("resurrectionTime");
        if (tag.hasUUID("corpseEntityId")) {
            this.corpseEntityId = tag.getUUID("corpseEntityId");
        }
        if (tag.contains("deathX")) {
            this.deathPos = new Vec3(tag.getDouble("deathX"), tag.getDouble("deathY"), tag.getDouble("deathZ"));
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("protectionExpiry", this.protectionExpiry);
        tag.putBoolean("isDead", this.isDead);
        tag.putLong("resurrectionTime", this.resurrectionTime);
        if (this.corpseEntityId != null) {
            tag.putUUID("corpseEntityId", this.corpseEntityId);
        }
        if (this.deathPos != null) {
            tag.putDouble("deathX", this.deathPos.x);
            tag.putDouble("deathY", this.deathPos.y);
            tag.putDouble("deathZ", this.deathPos.z);
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void serverTick() {
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning())
            return;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (this.isDead && player.isSpectator()
                && player.level().getGameTime() >= this.resurrectionTime) {
            // 复活逻辑
            DeathPenaltyComponent.KEY.get(serverPlayer).init();

            if (this.deathPos != null) {
                serverPlayer.teleportTo(this.deathPos.x, this.deathPos.y, this.deathPos.z);
            }
            serverPlayer.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);

            player.setHealth(player.getMaxHealth());

            var bd = findPlayerBodyEntity(serverPlayer);
            if (bd != null)
                bd.discard();

            TrainVoicePlugin.resetPlayer(player.getUUID());
            this.init();
            SRE.REPLAY_MANAGER.recordPlayerRevival(player.getUUID(), null);
            player.displayClientMessage(Component.translatable("message.noellesroles.defibrillator.revived"),
                    true);
        }

    }

    @Override
    public void clientTick() {
        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning())
            return;
        if (this.isDead && player.isSpectator()) {
            if (player.level().getGameTime() % 20 == 0) {
                player.displayClientMessage(Component.translatable("message.noellesroles.doctor.about_to_revive",
                        (this.resurrectionTime - this.player.level().getGameTime()) / 20), true);
            }
        }
    }
}