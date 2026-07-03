package org.agmas.noellesroles.game.roles.killer.embalmer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmbalmerPlayerComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<EmbalmerPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer"),
            EmbalmerPlayerComponent.class);

    public static final int MASQUERADE_COOLDOWN = 150 * 20;
    public static final int MASQUERADE_INITIAL_COOLDOWN = 150 * 20; // 开局2分半冷却
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
        masqueradeCooldown = MASQUERADE_INITIAL_COOLDOWN; // 开局进入2分半冷却
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
        if (masqueradeCooldown > 0) {
            masqueradeCooldown--;
            // 每秒同步一次（20 tick），冷却归零时强制同步
            if (masqueradeCooldown % 20 == 0 || masqueradeCooldown == 0) sync();
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
            if (masqueradeTicksLeft <= 0) {
                masqueradeActive = false;
                skinSwaps.clear();
                voicePitches.clear();
                // 发送清除数据包到所有客户端，重置皮肤和音调
                if (player instanceof ServerPlayer sp) {
                    for (ServerPlayer p : sp.serverLevel().getPlayers(p2 -> true)) {
                        ServerPlayNetworking.send(p, org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket.clear());
                    }
                }
            }
            // 每秒同步一次，状态结束时强制同步
            if (masqueradeTicksLeft % 20 == 0 || masqueradeTicksLeft <= 0) sync();
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeVarInt(masqueradeCooldown);
        buf.writeBoolean(masqueradeActive);
        buf.writeVarInt(masqueradeTicksLeft);
        // skinSwaps/voicePitches 通过 EmbalmerSkinSwapS2CPacket → ClientEmbalmerState 同步，无需经 CCA 重复发送
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        masqueradeCooldown = buf.readVarInt();
        masqueradeActive = buf.readBoolean();
        masqueradeTicksLeft = buf.readVarInt();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        // 使用 writeSyncPacket/applySyncPacket 紧凑二进制格式
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        // 使用 writeSyncPacket/applySyncPacket 紧凑二进制格式
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
