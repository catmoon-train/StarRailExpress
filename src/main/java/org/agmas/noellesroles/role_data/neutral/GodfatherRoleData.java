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

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.event.ShouldReloadDerringer;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class GodfatherRoleData extends SimpleRoleData {


    public final Set<UUID> familyMembers = new HashSet<>();
    public final Map<UUID, ResourceLocation> previousRoles = new HashMap<>();
    public int loadedBullets = 0;
    public int maxLoadedBullets = 3;
    public int recruitLimit = 4;
    public long recruitCooldownUntil = 0;
    public int recruitCooldownSeconds = 110;

    public GodfatherRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        familyMembers.clear();
        previousRoles.clear();
        loadedBullets = 0;
        maxLoadedBullets = 3;
        recruitLimit = 4;
        recruitCooldownUntil = 0;
        sync();
    }

    @Override
    public void clear() {
        init();
    }


    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return target == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {
        tag.putInt("LoadedBullets", loadedBullets);
        tag.putInt("MaxLoadedBullets", maxLoadedBullets);
        tag.putInt("RecruitLimit", recruitLimit);
        tag.putLong("RecruitCooldownUntil", recruitCooldownUntil);
        tag.putInt("RecruitCooldownSeconds", recruitCooldownSeconds);
        ListTag list = new ListTag();
        for (UUID id : familyMembers)
            list.add(StringTag.valueOf(id.toString()));
        tag.put("FamilyMembers", list);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider r) {
        loadedBullets = tag.getInt("LoadedBullets");
        maxLoadedBullets = tag.getInt("MaxLoadedBullets");
        recruitLimit = tag.getInt("RecruitLimit");
        recruitCooldownUntil = tag.getLong("RecruitCooldownUntil");
        recruitCooldownSeconds = tag.getInt("RecruitCooldownSeconds");
        familyMembers.clear();
        if (tag.contains("FamilyMembers", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("FamilyMembers", Tag.TAG_STRING))
                try {
                    familyMembers.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {
                }
        }
    }



    public static void registerEvents() {
        ShouldReloadDerringer.EVENT.register((victim, killer, deathReason) -> {
            if (RoleUtils.isPlayerTheJob(killer, ModRoles.GODFATHER)) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
    }
}
