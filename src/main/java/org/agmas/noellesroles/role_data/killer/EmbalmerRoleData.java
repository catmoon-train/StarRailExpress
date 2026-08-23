/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmbalmerRoleData extends SimpleRoleData {


    public static final int MASQUERADE_COOLDOWN = 150 * 20;
    public static final int MASQUERADE_INITIAL_COOLDOWN = 150 * 20; // 开局2分半冷却
    public static final int MASQUERADE_DURATION = 30 * 20;
    public static final int PITCH_MIN = 70;
    public static final int PITCH_MAX = 130;

    public int masqueradeCooldown;
    public boolean masqueradeActive;
    public int masqueradeTicksLeft;
    // Sync: swap map + pitch map as UUID-string pairs
    public Map<UUID, UUID> skinSwaps = new HashMap<>();
    public Map<UUID, Float> voicePitches = new HashMap<>();

    // 用于检测激活状态边界，避免每秒同步
    private boolean prevMasqueradeActive = false;

    public EmbalmerRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        masqueradeCooldown = MASQUERADE_INITIAL_COOLDOWN; // 开局进入2分半冷却
        masqueradeActive = false;
        masqueradeTicksLeft = 0;
        skinSwaps.clear();
        voicePitches.clear();
        prevMasqueradeActive = false;
        sync();
    }

    @Override
    public void clear() {
        init();
    }


    public boolean isActive() {
        if (player == null || player.level().isClientSide())
            return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.EMBALMER);
    }

    @Override
    public void serverTick() {
        if (!isActive())
            return;
        boolean changed = false;
        if (masqueradeCooldown > 0) {
            masqueradeCooldown--;
            if (masqueradeCooldown == 0)
                changed = true; // 冷却归零边界
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
                changed = true; // 激活结束边界
            }
        }
        // 仅在状态边界同步（替代原先每秒同步）：冷却归零、激活开始/结束。
        // 客户端 HUD 自行平滑秒数显示，因此无需每秒发包。
        if (masqueradeActive != prevMasqueradeActive)
            changed = true;
        if (changed)
            sync();
        prevMasqueradeActive = masqueradeActive;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("masqueradeCooldown", masqueradeCooldown);
        tag.putBoolean("masqueradeActive", masqueradeActive);
        tag.putInt("masqueradeTicksLeft", masqueradeTicksLeft);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        masqueradeCooldown = tag.getInt("masqueradeCooldown");
        masqueradeActive = tag.getBoolean("masqueradeActive");
        masqueradeTicksLeft = tag.getInt("masqueradeTicksLeft");
    }

    /**
     * Get voice pitch for a player during active masquerade. Returns 1.0F if not
     * active or not found.
     */
    public static float getVoicePitch(Player player) {
        if (player == null || player.level().isClientSide())
            return 1.0F;
        var comp = RoleData.getNullable(EmbalmerRoleData.class, player);
        if (!RoleData.isAttached(comp) || !comp.masqueradeActive)
            return 1.0F;
        return comp.voicePitches.getOrDefault(player.getUUID(), 1.0F);
    }



    @Override
    public void clientTick() {
        if (masqueradeCooldown > 0) {
            masqueradeCooldown--;
        }
        if (masqueradeActive && masqueradeTicksLeft > 0) {
            masqueradeTicksLeft--;
        }
    }
}
