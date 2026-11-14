package org.agmas.noellesroles.role_data.neutral;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilRole;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilRules;
import org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilRefugeeState;

/** 每名恶魔独立持有种子与胜场；开局人数一经记录不随存活人数改变。 */
public final class RouletteDevilRoleData extends SimpleRoleData {
    public int startingPlayers;
    public int wins;
    public int losses;
    public long manifestUntil;
    public final RouletteDevilRefugeeState refugee = new RouletteDevilRefugeeState();
    public CompoundTag refugeeReturn = new CompoundTag();
    public final Map<UUID, Long> seeds = new HashMap<>();
    public final Set<UUID> notified = new HashSet<>();
    /** 同一目标再次挑战的解锁时刻；赌局结束时写入，中途终止同样计入。 */
    public final Map<UUID, Long> retryAt = new HashMap<>();
    public RouletteDevilRoleData(RoleDataContext ctx) { super(ctx); }
    public int required() { return RouletteDevilRules.requiredWins(startingPlayers); }
    public int lives() { return Math.max(0, RouletteDevilRules.defeatAllowances(startingPlayers) - losses); }
    public boolean mature(UUID target, long now) {
        Long planted = seeds.get(target);
        return planted != null && RouletteDevilRules.mature(planted, now);
    }
    /** 目标仍在同目标挑战冷却中。 */
    public boolean cooling(UUID target, long now) {
        Long readyAt = retryAt.get(target);
        return readyAt != null && RouletteDevilRules.cooling(readyAt, now);
    }
    public int cooldownSeconds(UUID target, long now) {
        Long readyAt = retryAt.get(target);
        return readyAt == null ? 0 : RouletteDevilRules.cooldownSeconds(readyAt, now);
    }
    public void startCooldown(UUID target, long now) {
        if (target != null) retryAt.put(target, now + RouletteDevilRules.SAME_TARGET_COOLDOWN_TICKS);
    }
    @Override public void serverTick() {
        if (player instanceof ServerPlayer serverPlayer) RouletteDevilRole.tick(serverPlayer, this);
    }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Starting", startingPlayers); tag.putInt("Wins", wins); tag.putInt("Losses", losses);
        tag.putLong("Manifest", manifestUntil);
        tag.putBoolean("RefugeeSuppressed", refugee.pending());
        tag.putInt("RefugeeReturnGameType", refugee.returnGameType());
        ListTag cooldowns = new ListTag();
        retryAt.forEach((id, readyAt) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("Target", id); row.putLong("ReadyAt", readyAt);
            cooldowns.add(row);
        });
        tag.put("Cooldowns", cooldowns);
        tag.put("RefugeeReturn", refugeeReturn.copy());
        ListTag list = new ListTag();
        seeds.forEach((id, at) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("Target", id); row.putLong("At", at);
            row.putBoolean("Notified", notified.contains(id)); list.add(row);
        });
        tag.put("Seeds", list);
    }
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registries) {
        startingPlayers = Math.max(0, tag.getInt("Starting")); wins = Math.max(0, tag.getInt("Wins"));
        losses = Math.max(0, tag.getInt("Losses")); manifestUntil = tag.getLong("Manifest");
        refugee.load(tag.getBoolean("RefugeeSuppressed"), tag.contains("RefugeeReturnGameType") ? tag.getInt("RefugeeReturnGameType") : 2);
        refugeeReturn = tag.getCompound("RefugeeReturn").copy();
        seeds.clear(); notified.clear(); retryAt.clear();
        for (Tag entry : tag.getList("Seeds", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("Target")) continue;
            UUID id = row.getUUID("Target"); seeds.put(id, row.getLong("At"));
            if (row.getBoolean("Notified")) notified.add(id);
        }
        for (Tag entry : tag.getList("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("Target")) continue;
            retryAt.put(row.getUUID("Target"), row.getLong("ReadyAt"));
        }
    }
}
