package org.agmas.noellesroles.game.roles.killer.embalmer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

public class EmbalmerPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<EmbalmerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer"),
            EmbalmerPlayerComponent.class);

    public static final int MASQUERADE_COOLDOWN = 80 * 20;
    public static final int MASQUERADE_DURATION = 30 * 20;
    public static final int PITCH_MIN = 70;
    public static final int PITCH_MAX = 130;

    private final Player player;
    public int masqueradeCooldown;
    public boolean masqueradeActive;
    public int masqueradeTicksLeft;
    // Sync: swap map + pitch map as UUID-string pairs
    public Map<UUID, UUID> skinSwaps = new HashMap<>();
    public Map<UUID, Float> voicePitches = new HashMap<>();

    public EmbalmerPlayerComponent(Player player) { this.player = player; }
    @Override public Player getPlayer() { return player; }
    @Override public void init() {
        masqueradeCooldown = 0;
        masqueradeActive = false;
        masqueradeTicksLeft = 0;
        skinSwaps.clear();
        voicePitches.clear();
        sync();
    }
    @Override public void clear() { init(); }
    public void sync() { KEY.sync(player); }

    public boolean isActive() {
        if (player == null || player.level().isClientSide()) return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.EMBALMER);
    }

    @Override
    public void serverTick() {
        if (!isActive()) return;
        if (masqueradeCooldown > 0) { masqueradeCooldown--; sync(); }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
            if (masqueradeTicksLeft <= 0) {
                masqueradeActive = false;
                skinSwaps.clear();
                voicePitches.clear();
            }
            sync();
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("masqCd", masqueradeCooldown);
        tag.putBoolean("masqActive", masqueradeActive);
        tag.putInt("masqLeft", masqueradeTicksLeft);
        // Write maps as ListTag of compounds
        net.minecraft.nbt.ListTag swaps = new net.minecraft.nbt.ListTag();
        for (var e : skinSwaps.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey().toString());
            c.putString("v", e.getValue().toString());
            swaps.add(c);
        }
        tag.put("swaps", swaps);
        net.minecraft.nbt.ListTag pitches = new net.minecraft.nbt.ListTag();
        for (var e : voicePitches.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("k", e.getKey().toString());
            c.putFloat("v", e.getValue());
            pitches.add(c);
        }
        tag.put("pitches", pitches);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        masqueradeCooldown = tag.getInt("masqCd");
        masqueradeActive = tag.getBoolean("masqActive");
        masqueradeTicksLeft = tag.getInt("masqLeft");
        skinSwaps.clear();
        for (var t : tag.getList("swaps", 10)) {
            CompoundTag c = (CompoundTag) t;
            skinSwaps.put(UUID.fromString(c.getString("k")), UUID.fromString(c.getString("v")));
        }
        voicePitches.clear();
        for (var t : tag.getList("pitches", 10)) {
            CompoundTag c = (CompoundTag) t;
            voicePitches.put(UUID.fromString(c.getString("k")), c.getFloat("v"));
        }
    }

    /** Get voice pitch for a player during active masquerade. Returns 1.0F if not active or not found. */
    public static float getVoicePitch(Player player) {
        if (player == null || player.level().isClientSide()) return 1.0F;
        var comp = KEY.get(player);
        if (comp == null || !comp.masqueradeActive) return 1.0F;
        return comp.voicePitches.getOrDefault(player.getUUID(), 1.0F);
    }

    @Override public void writeToNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
    @Override public void readFromNbt(CompoundTag tag, HolderLookup.Provider lookup) {}
}
