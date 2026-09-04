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

package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 纵火犯职业数据：被浇油的玩家与点燃归属都记在纵火犯身上，而不是每个受害者一份 CCA。
 */
public class ArsonistRoleData extends SimpleRoleData {

    public int dousedCount = 0;
    public final Set<UUID> dousedPlayers = new LinkedHashSet<>();
    public final Map<UUID, UUID> burningKillers = new HashMap<>();

    public ArsonistRoleData(RoleDataContext context) {
        super(context);
    }

    @Nullable
    public static ArsonistRoleData of(Player player) {
        return RoleData.getNullable(ArsonistRoleData.class, player);
    }

    @Nullable
    public static ArsonistRoleData find(Level level) {
        if (level == null) {
            return null;
        }
        for (Player p : level.players()) {
            ArsonistRoleData data = of(p);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    public boolean isDoused(UUID playerId) {
        return playerId != null && dousedPlayers.contains(playerId);
    }

    public void douse(Player victim) {
        if (victim == null) {
            return;
        }
        if (dousedPlayers.add(victim.getUUID())) {
            dousedCount++;
            sync();
        }
    }

    public void undouse(UUID playerId) {
        if (playerId != null && dousedPlayers.remove(playerId)) {
            sync();
        }
    }

    public void setBurningKiller(UUID victimId, @Nullable UUID killerId) {
        if (victimId == null) {
            return;
        }
        if (killerId == null) {
            burningKillers.remove(victimId);
        } else {
            burningKillers.put(victimId, killerId);
        }
        sync();
    }

    @Nullable
    public UUID getBurningKiller(UUID victimId) {
        return victimId == null ? null : burningKillers.get(victimId);
    }

    public void resetDouses() {
        dousedPlayers.clear();
        dousedCount = 0;
        sync();
    }

    public void reset() {
        dousedPlayers.clear();
        dousedCount = 0;
        burningKillers.clear();
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("dousedCount", dousedCount);
        ListTag doused = new ListTag();
        for (UUID id : dousedPlayers) {
            doused.add(NbtUtils.createUUID(id));
        }
        tag.put("dousedPlayers", doused);
        ListTag burning = new ListTag();
        for (Map.Entry<UUID, UUID> entry : burningKillers.entrySet()) {
            CompoundTag pair = new CompoundTag();
            pair.putUUID("v", entry.getKey());
            pair.putUUID("k", entry.getValue());
            burning.add(pair);
        }
        tag.put("burningKillers", burning);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        dousedCount = tag.contains("dousedCount") ? tag.getInt("dousedCount") : 0;
        dousedPlayers.clear();
        ListTag doused = tag.getList("dousedPlayers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < doused.size(); i++) {
            dousedPlayers.add(NbtUtils.loadUUID(doused.get(i)));
        }
        burningKillers.clear();
        ListTag burning = tag.getList("burningKillers", Tag.TAG_COMPOUND);
        for (int i = 0; i < burning.size(); i++) {
            CompoundTag pair = burning.getCompound(i);
            if (pair.hasUUID("v") && pair.hasUUID("k")) {
                burningKillers.put(pair.getUUID("v"), pair.getUUID("k"));
            }
        }
    }
}
