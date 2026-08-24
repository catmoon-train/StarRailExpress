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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.UUID;

public class FortunetellerRoleData extends SimpleRoleData {

    public ArrayList<ProtectedInfo> protectedPlayers = new ArrayList<>();
    /** 过期时间 */
    public static final int MAX_PROTECT_TIME = 120 * 20;

    @Override
    public void init() {
        this.protectedPlayers.clear();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public FortunetellerRoleData(RoleDataContext context) {
        super(context);
    }

    public void clientTick() {
        for (var ppi : protectedPlayers) {
            if (ppi.time > 0)
                ppi.time--;
        }
    }

    public void protectPlayer(Player target) {
        if (player instanceof ServerPlayer)
            ConfigWorldComponent.onPlayerUsedSkill((ServerPlayer) player);

        UUID puid = target.getUUID();
        protectedPlayers.add(new ProtectedInfo(puid, MAX_PROTECT_TIME));
        player.displayClientMessage(Component.translatable("message.fortuneteller.protected", target.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(
                Component.translatable("message.fortuneteller.been_protected", player.getDisplayName())
                        .withStyle(ChatFormatting.GOLD),
                true);
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.fortuneteller",
                        GameReplayUtils.getReplayPlayerDisplayText(player, true),
                        GameReplayUtils.getReplayPlayerDisplayText(target, true)));
        this.sync();
    }

    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.FORTUNETELLER)) {
            return;
        }
        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }
        boolean shouldSync = false;
        boolean shouldDelete = false;
        if (player.level().getGameTime() % 200 == 0)
            shouldSync = true;
        for (var ppi : protectedPlayers) {
            ppi.time--;
            if (ppi.time <= 0) {
                shouldDelete = true;
                shouldSync = true;
            }
        }
        if (player.level().getGameTime() % 40 == 0 || shouldDelete) {
            protectedPlayers.removeIf((ppi) -> {
                if ((this.player.level().getPlayerByUUID(ppi.player)) == null) {
                    return true;
                }
                return ppi.time <= 0;
            });
        }
        if (shouldSync)
            this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag targetListTag = new ListTag();
        for (ProtectedInfo targetInfo : protectedPlayers) {
            CompoundTag targetTag = new CompoundTag();
            if (targetInfo.player != null) {
                targetTag.putUUID("player", targetInfo.player);
            }
            if (targetInfo.time >= 0) {
                targetTag.putInt("time", targetInfo.time);
            }
            targetListTag.add(targetTag);
        }
        tag.put("targetList", targetListTag);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        protectedPlayers.clear();
        if (tag.contains("targetList")) {
            ListTag targetListTag = tag.getList("targetList", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetListTag.size(); i++) {
                CompoundTag targetTag = targetListTag.getCompound(i);
                UUID targetPlayer = targetTag.contains("player") ? targetTag.getUUID("player") : null;
                int time = targetTag.contains("time") ? targetTag.getInt("time") : null;
                protectedPlayers.add(new ProtectedInfo(targetPlayer, time));
            }
        }
    }

    public static class ProtectedInfo {
        public UUID player;
        public int time;

        public ProtectedInfo(UUID player, int time) {
            this.player = player;
            this.time = time;
        }
    }

    public boolean hasProtected(Player player2) {
        return this.protectedPlayers.stream().anyMatch((p) -> {
            return player2.getUUID().equals(p.player);
        });
    }

    public boolean triggerProtect(Player player2) {
        if (hasProtected(player2)) {
            this.protectedPlayers.removeIf((p) -> {
                return player2.getUUID().equals(p.player);
            });
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            player2.displayClientMessage(Component.translatable("message.fortuneteller.protection_active_self")
                    .withStyle(ChatFormatting.AQUA), true);
            player.displayClientMessage(
                    Component.translatable("message.fortuneteller.protection_active_role", player2.getDisplayName())
                            .withStyle(ChatFormatting.AQUA),
                    true);
            player2.stopRiding();
            player2.stopSleeping();
            player2.teleportTo(x, y, z);
            return true;
        }
        return false;
    }

}
