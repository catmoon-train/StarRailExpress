package io.wifi.events.day_night_fight.cca;

import io.wifi.events.day_night_fight.DNFRoles;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

public class DNFKillerStatsComponent implements RoleComponent {
    public static final ComponentKey<DNFKillerStatsComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_killer_stats"), DNFKillerStatsComponent.class);

    private final Player player;
    private int blood;
    private int bodiesEaten;
    private int soldierEaten;
    private int psychologistEaten;
    private int locksmithEaten;
    private int civilianEaten;
    private long lastCorpseEatTick;
    private boolean personalEnding;
    private boolean hungerWarned;

    public DNFKillerStatsComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
        this.lastCorpseEatTick = player.level().getGameTime();
        sync();
    }

    @Override
    public void clear() {
        this.blood = 0;
        this.bodiesEaten = 0;
        this.soldierEaten = 0;
        this.psychologistEaten = 0;
        this.locksmithEaten = 0;
        this.civilianEaten = 0;
        this.lastCorpseEatTick = 0;
        this.personalEnding = false;
        this.hungerWarned = false;
    }

    public void sync() {
        KEY.sync(this.player);
    }

    public int getBlood() {
        return blood;
    }

    public int getBodiesEaten() {
        return bodiesEaten;
    }

    public boolean hasPersonalEnding() {
        return personalEnding;
    }

    public float getTransformationProgress() {
        float soldier = Math.min(1f, soldierEaten / 1f);
        float psycho = Math.min(1f, psychologistEaten / 1f);
        float locksmith = Math.min(1f, locksmithEaten / 1f);
        float civilians = Math.min(1f, civilianEaten / 2f);
        return (soldier + psycho + locksmith + civilians) / 4f;
    }

    public void eatCorpse(ResourceLocation roleId, int bloodGain) {
        this.blood += bloodGain;
        this.bodiesEaten++;
        this.lastCorpseEatTick = player.level().getGameTime();
        this.hungerWarned = false;

        if (roleId.equals(DNFRoles.SOLDIER_ID)) {
            soldierEaten++;
        } else if (roleId.equals(DNFRoles.PSYCHOLOGIST_ID)) {
            psychologistEaten++;
        } else if (roleId.equals(DNFRoles.LOCKSMITH_ID)) {
            locksmithEaten++;
        } else if (roleId.equals(DNFRoles.CIVILIAN_ID)
                || roleId.equals(io.wifi.starrailexpress.api.TMMRoles.CIVILIAN.identifier())) {
            civilianEaten++;
        }

        if (!personalEnding && soldierEaten >= 1 && psychologistEaten >= 1 && locksmithEaten >= 1
                && civilianEaten >= 2) {
            personalEnding = true;
        }
        sync();
    }

    public boolean spendBlood(int amount) {
        if (blood < amount) {
            return false;
        }
        blood -= amount;
        sync();
        return true;
    }

    public void setBlood(int blood) {
        this.blood = Math.max(0, blood);
        sync();
    }

    public void addBlood(int amount) {
        setBlood(this.blood + amount);
    }

    public long getLastCorpseEatTick() {
        return lastCorpseEatTick;
    }

    public void setLastCorpseEatTick(long lastCorpseEatTick) {
        this.lastCorpseEatTick = lastCorpseEatTick;
    }

    public boolean isHungerWarned() {
        return hungerWarned;
    }

    public void setHungerWarned(boolean hungerWarned) {
        this.hungerWarned = hungerWarned;
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToSyncNbt(tag, registryLookup);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("Blood", blood);
        tag.putInt("BodiesEaten", bodiesEaten);
        tag.putInt("SoldierEaten", soldierEaten);
        tag.putInt("PsychologistEaten", psychologistEaten);
        tag.putInt("LocksmithEaten", locksmithEaten);
        tag.putInt("CivilianEaten", civilianEaten);
        tag.putLong("LastCorpseEatTick", lastCorpseEatTick);
        tag.putBoolean("PersonalEnding", personalEnding);
        tag.putBoolean("HungerWarned", hungerWarned);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.blood = tag.getInt("Blood");
        this.bodiesEaten = tag.getInt("BodiesEaten");
        this.soldierEaten = tag.getInt("SoldierEaten");
        this.psychologistEaten = tag.getInt("PsychologistEaten");
        this.locksmithEaten = tag.getInt("LocksmithEaten");
        this.civilianEaten = tag.getInt("CivilianEaten");
        this.lastCorpseEatTick = tag.getLong("LastCorpseEatTick");
        this.personalEnding = tag.getBoolean("PersonalEnding");
        this.hungerWarned = tag.getBoolean("HungerWarned");
    }
}
