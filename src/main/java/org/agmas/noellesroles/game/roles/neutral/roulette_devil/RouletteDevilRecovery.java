package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.agmas.noellesroles.init.ModEffects;

/** 专属局的返程保险：断线/重启后恢复原背包与位置，绝不将赌局补给带回列车。 */
final class RouletteDevilRecovery extends SavedData {
    private final Map<UUID, CompoundTag> pending = new HashMap<>();
    static RouletteDevilRecovery get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(RouletteDevilRecovery::new, RouletteDevilRecovery::load, null), "sre_roulette_devil_return");
    }
    void capture(ServerPlayer p) {
        if (pending.containsKey(p.getUUID())) throw new IllegalStateException("Unrestored roulette inventory");
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", p.level().dimension().location().toString());
        tag.putDouble("X", p.getX()); tag.putDouble("Y", p.getY()); tag.putDouble("Z", p.getZ());
        tag.putFloat("Yaw", p.getYRot()); tag.putFloat("Pitch", p.getXRot());
        tag.putInt("Selected", p.getInventory().selected);
        tag.put("Inventory", p.getInventory().save(new ListTag()));
        tag.putLong("Captured", p.getServer().overworld().getGameTime());
        var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(p.level()).getRole(p);
        if (role != null && role.getMoodType() == io.wifi.starrailexpress.api.SRERole.MoodType.REAL) {
            tag.putFloat("DuelMood", io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(p).getMood());
            var immunity = p.getEffect(ModEffects.MOOD_DRAIN_IMMUNITY);
            if (immunity != null) tag.put("MoodImmunity", immunity.save());
        }
        MobEffectInstance ban = p.getEffect(ModEffects.SKILL_BANED);
        if (ban != null) tag.put("SkillBan", ban.save());
        pending.put(p.getUUID(), tag); setDirty();
    }
    boolean restore(ServerPlayer p) {
        CompoundTag tag = pending.get(p.getUUID());
        if (tag == null) return false;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        ServerLevel destination = id == null ? null : p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (destination == null) destination = p.getServer().overworld();
        p.stopRiding(); p.setNoGravity(false); p.setDeltaMovement(0, 0, 0); p.fallDistance = 0;
        p.teleportTo(destination, tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), java.util.Set.of(), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        p.getInventory().clearContent(); p.getInventory().load(tag.getList("Inventory", Tag.TAG_COMPOUND));
        p.getInventory().selected = Math.clamp(tag.getInt("Selected"), 0, 8);
        p.getInventory().setChanged(); p.inventoryMenu.broadcastChanges();
        p.removeEffect(ModEffects.DOMAIN_MARK); p.removeEffect(ModEffects.BLACK_MONITOR); p.removeEffect(ModEffects.SKILL_BANED);
        restoreEffect(p, tag, "SkillBan");
        if (tag.contains("DuelMood")) {
            var current = p.getEffect(ModEffects.MOOD_DRAIN_IMMUNITY);
            if (current != null && current.isInfiniteDuration() && current.getAmplifier() == 0
                    && current.isAmbient() && !current.isVisible() && !current.showIcon())
                p.removeEffect(ModEffects.MOOD_DRAIN_IMMUNITY);
            if (!p.hasEffect(ModEffects.MOOD_DRAIN_IMMUNITY)) restoreEffect(p, tag, "MoodImmunity");
            io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(p).setMood(tag.getFloat("DuelMood"));
        }
        pending.remove(p.getUUID()); setDirty(); return true;
    }
    private static void restoreEffect(ServerPlayer p, CompoundTag tag, String key) {
        if (!tag.contains(key)) return;
        MobEffectInstance previous = MobEffectInstance.load(tag.getCompound(key));
        if (previous == null) return;
        int elapsed = (int) Math.clamp(p.getServer().overworld().getGameTime() - tag.getLong("Captured"), 0, Integer.MAX_VALUE);
        int duration = previous.isInfiniteDuration() ? -1 : previous.getDuration() - elapsed;
        if (duration > 0 || previous.isInfiniteDuration())
            p.addEffect(new MobEffectInstance(previous.getEffect(), duration, previous.getAmplifier(),
                    previous.isAmbient(), previous.isVisible(), previous.showIcon()));
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pending.forEach((id, state) -> { CompoundTag row = state.copy(); row.putUUID("Player", id); list.add(row); });
        tag.put("Returns", list); return tag;
    }
    private static RouletteDevilRecovery load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new RouletteDevilRecovery();
        for (Tag row : tag.getList("Returns", Tag.TAG_COMPOUND)) {
            CompoundTag state = (CompoundTag) row;
            if (state.hasUUID("Player")) data.pending.put(state.getUUID("Player"), state.copy());
        }
        return data;
    }
}
