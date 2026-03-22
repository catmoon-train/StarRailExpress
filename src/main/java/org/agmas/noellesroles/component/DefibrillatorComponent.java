package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class DefibrillatorComponent implements RoleComponent {
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
        ModComponents.DEFIBRILLATOR.sync(player);
    }

    @Override
    public void init() {
        this.protectionExpiry = 0;
        this.isDead = false;
        this.resurrectionTime = 0;
        this.corpseEntityId = null;
        this.deathPos = null;
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
}