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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * 鹈鹕职业数据。冷却用 {@link #eatCooldownUntil} 时间戳，只在吞噬成功/冷却结束时同步。
 */
public class PelicanRoleData extends SimpleRoleData {

    public static final int INSTINCT_RANGE = 25;

    public int eatenCount = 0;
    public int requiredEaten = 1;
    public List<String> bellyNames = new ArrayList<>();
    public List<UUID> bellyPlayerIds = new ArrayList<>();
    public Set<UUID> uniqueEaten = new HashSet<>();
    public long eatCooldownUntil = 0;

    public PelicanRoleData(RoleDataContext context) {
        super(context);
    }

    public int getRemainingCooldownTicks() {
        if (eatCooldownUntil <= 0) {
            return 0;
        }
        return (int) Math.max(0, eatCooldownUntil - player.level().getGameTime());
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        int totalParticipants = gameWorld.getPlayerCount();
        double percent = NoellesRolesConfig.HANDLER.instance().pelicanEatPercentage;
        int newRequired = Math.max(1, (int) Math.ceil(totalParticipants * (percent / 100.0D)) - 1);
        if (requiredEaten != newRequired) {
            requiredEaten = newRequired;
            sync();
        }

        if (eatCooldownUntil > 0 && player.level().getGameTime() >= eatCooldownUntil) {
            eatCooldownUntil = 0;
            sync();
        }
    }

    public boolean tryEat(ServerPlayer target) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        if (target == null) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        if (target.getUUID().equals(player.getUUID())) {
            return false;
        }

        if (eatCooldownUntil > 0 && player.level().getGameTime() < eatCooldownUntil) {
            long remaining = Math.max(1, (eatCooldownUntil - player.level().getGameTime()) / 20);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.cooldown", remaining)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        if (bellyPlayerIds.contains(target.getUUID())) {
            return false;
        }
        if (PelicanManager.isStashed(target)) {
            return false;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorld.isRole(target, ModRoles.PELICAN)) {
            return false;
        }
        if (gameWorld.isRole(target, ModRoles.RAVEN)) {
            return false;
        }
        if (gameWorld.isRole(target, ModRoles.MONOKUMA)) {
            return false;
        }
        if (gameWorld.isRole(target, TMMRoles.LOOSE_END)) {
            return false;
        }
        WorldModifierComponent worldModifier = WorldModifierComponent.KEY.get(target.level());
        if (worldModifier.isModifier(target, SEModifiers.SPLIT_PERSONALITY)) {
            return false;
        }

        if (gameWorld.isRole(target, ModRoles.PUPPETEER)) {
            return false;
        }
        var puppeteerComp = RoleData.getNullable(PuppeteerRoleData.class, target);
        if (RoleData.isAttached(puppeteerComp) && puppeteerComp.isControllingPuppet) {
            return false;
        }

        PelicanManager.stashPlayer(sp, target);

        if (org.agmas.noellesroles.game.roles.neutral.leader.LeaderFollowerEffects.isFollowerOfLeader(sp)
                && sp.getRandom().nextInt(100) < 40) {
            eatCooldownUntil = 0;
        } else {
            eatCooldownUntil = player.level().getGameTime() + 35 * 20L;
        }

        bellyPlayerIds.add(target.getUUID());
        bellyNames.add(target.getName().getString());
        uniqueEaten.add(target.getUUID());
        eatenCount = uniqueEaten.size();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BURP, SoundSource.MASTER, 0.9F, 0.65F);

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.pelican.swallowed",
                        target.getName().getString(), eatenCount, requiredEaten)
                        .withStyle(ChatFormatting.GOLD),
                true);

        sync();
        checkWinCondition();
        return true;
    }

    public boolean releaseLast() {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        bellyPlayerIds.removeIf(id -> !PelicanManager.isStashed(id));
        bellyNames.clear();
        for (UUID id : bellyPlayerIds) {
            ServerPlayer p = player.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                bellyNames.add(p.getName().getString());
            }
        }
        if (bellyPlayerIds.isEmpty()) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.belly_empty")
                            .withStyle(ChatFormatting.RED),
                    true);
            sync();
            return false;
        }

        UUID targetId = bellyPlayerIds.remove(bellyPlayerIds.size() - 1);
        if (!bellyNames.isEmpty()) {
            bellyNames.remove(bellyNames.size() - 1);
        }

        ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            PelicanManager.releasePlayer(target);
            SRE.REPLAY_MANAGER.recordCustomEvent(Component.translatable("replay.pelican.spit",
                    GameReplayUtils.getReplayPlayerDisplayText(sp, true),
                    GameReplayUtils.getReplayPlayerDisplayText(target, true)));
        }

        sp.displayClientMessage(
                Component.translatable("message.noellesroles.pelican.released_one")
                        .withStyle(ChatFormatting.GREEN),
                true);
        sync();
        return true;
    }

    public void checkWinCondition() {
        if (eatenCount >= requiredEaten && requiredEaten > 0) {
            if (player.level() instanceof ServerLevel serverLevel) {
                RoleUtils.customWinnerWin(serverLevel,
                        GameUtils.WinStatus.CUSTOM,
                        ModRoles.PELICAN_ID.getPath(),
                        OptionalInt.of(ModRoles.PELICAN.color()));
            }
        }
    }

    public static boolean checkPelicanVictory(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        for (ServerPlayer sp : serverLevel.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
                continue;
            }
            if (!gameWorld.isRole(sp, ModRoles.PELICAN)) {
                continue;
            }
            PelicanRoleData data = RoleData.getNullable(PelicanRoleData.class, sp);
            if (RoleData.isAttached(data) && data.eatenCount >= data.requiredEaten && data.requiredEaten > 0) {
                RoleUtils.customWinnerWin(serverLevel,
                        GameUtils.WinStatus.CUSTOM,
                        ModRoles.PELICAN_ID.getPath(),
                        OptionalInt.of(ModRoles.PELICAN.color()));
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("EatenCount", eatenCount);
        tag.putInt("RequiredEaten", requiredEaten);
        tag.putLong("EatCooldownUntil", eatCooldownUntil);

        ListTag nameList = new ListTag();
        for (String name : bellyNames) {
            nameList.add(StringTag.valueOf(name));
        }
        tag.put("BellyNames", nameList);

        ListTag idList = new ListTag();
        for (UUID id : bellyPlayerIds) {
            idList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("BellyPlayerIds", idList);

        ListTag uniqueList = new ListTag();
        for (UUID id : uniqueEaten) {
            uniqueList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("UniqueEaten", uniqueList);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        eatenCount = tag.getInt("EatenCount");
        requiredEaten = tag.getInt("RequiredEaten");
        eatCooldownUntil = tag.getLong("EatCooldownUntil");

        bellyNames.clear();
        if (tag.contains("BellyNames", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BellyNames", Tag.TAG_STRING);
            for (Tag t : list) {
                bellyNames.add(t.getAsString());
            }
        }

        bellyPlayerIds.clear();
        if (tag.contains("BellyPlayerIds", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BellyPlayerIds", Tag.TAG_STRING);
            for (Tag t : list) {
                try {
                    bellyPlayerIds.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {
                }
            }
        }

        uniqueEaten.clear();
        if (tag.contains("UniqueEaten", Tag.TAG_LIST)) {
            ListTag list = tag.getList("UniqueEaten", Tag.TAG_STRING);
            for (Tag t : list) {
                try {
                    uniqueEaten.add(UUID.fromString(t.getAsString()));
                } catch (Exception ignored) {
                }
            }
        }

        if (player.level().isClientSide) {
            io.wifi.starrailexpress.client.SREClient.cachedHighLightMap.clear();
        }
    }
}
