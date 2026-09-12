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

package io.wifi.starrailexpress.game.modes.funny.mob;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.morph.MorphApi;
import io.wifi.starrailexpress.network.PlayerDeathPayload;
import io.wifi.starrailexpress.network.packet.MobRiotStateS2CPacket;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.exmo.sre.meeting.MeetingManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SREMobRiotGameMode extends SRERoleRotationGameMode {
    public static final int DAY_TICKS = 60 * 20;
    public static final int NIGHT_TICKS = 90 * 20;
    public static final int INVIS_TICKS = 10 * 20;
    public static final int DAY_GOLD = 30;
    public static final int NIGHT_GOLD = 30;
    public static final int ESCAPE_GOLD = 45;
    public static final int INNOCENT_MINIGAME_GOLD = 60;
    public static final int KILLER_MINIGAME_GOLD = 10;
    public static final int SLEEP_CONFIRM_TICKS = 40;
    public static final float DAY_SAN = 0.2f;
    private static final long MC_DAY_TIME = 1000L;
    private static final long MC_NIGHT_TIME = 18000L;

    public enum Phase {
        INACTIVE,
        DAY,
        NIGHT
    }

    private Phase phase = Phase.INACTIVE;
    private boolean cycleActive = false;
    private long phaseEndTick = -1L;
    private long nightGrantAtTick = -1L;
    private boolean nightRewardsGranted = true;
    private boolean escapeUnlocked = false;
    private int startingPlayerCount = 0;
    private final Set<UUID> escaped = new HashSet<>();
    private final Map<UUID, Integer> sleepTicks = new HashMap<>();

    public SREMobRiotGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public void initializeGame(ServerLevel world, SREGameWorldComponent gameComp, List<ServerPlayer> players) {
        resetState();
        this.startingPlayerCount = players.size();
        super.initializeGame(world, gameComp, players);
    }

    public void onTrueStarted(ServerLevel world) {
        if (this.startingPlayerCount <= 0) {
            this.startingPlayerCount = SREGameWorldComponent.KEY.get(world).getStartingPlayerCount();
            if (this.startingPlayerCount <= 0) {
                this.startingPlayerCount = world.getServer().getPlayerCount();
            }
        }
        this.cycleActive = true;
        beginPhase(world, Phase.DAY);
    }

    @Override
    public void tickServerGameLoop(ServerLevel world, SREGameWorldComponent gameComp) {
        super.tickServerGameLoop(world, gameComp);
        if (!this.cycleActive || !gameComp.isRunning()) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(world);
        if (this.phase == Phase.NIGHT && !this.nightRewardsGranted && now >= this.nightGrantAtTick) {
            grantNightRewards(world, gameComp);
            this.nightRewardsGranted = true;
        }
        if (now >= this.phaseEndTick) {
            beginPhase(world, this.phase == Phase.DAY ? Phase.NIGHT : Phase.DAY);
        }
        if (!this.escapeUnlocked) {
            tryUnlock(world);
        } else {
            tickEscapeSleep(world, gameComp);
        }
        if (world.getGameTime() % 20L == 0L) {
            syncToPlayers(world);
        }
    }

    @Override
    public void finalizeGame(ServerLevel world, SREGameWorldComponent gameComp) {
        clearNightEffects(world);
        resetState();
        syncToPlayers(world);
        super.finalizeGame(world, gameComp);
    }

    @Override
    public boolean suppressMoodTasks() {
        return true;
    }

    @Override
    public boolean shouldGrantDefaultQuestMoneyOnFinish() {
        return false;
    }

    @Override
    public boolean usesIndependentMinigameTasks() {
        return true;
    }

    @Override
    public float getMinigameTaskIntervalMultiplier() {
        return this.cycleActive && this.phase == Phase.DAY ? 0.5f : 1f;
    }

    @Override
    public boolean canHaveMeetingVote() {
        return !this.cycleActive || this.phase == Phase.DAY;
    }

    @Override
    public WinStatus allowGameEnd(ServerLevel world, WinStatus winStatus, boolean looseEnds,
            SREGameWorldComponent gameComp) {
        if (!this.escapeUnlocked) {
            return super.allowGameEnd(world, winStatus, looseEnds, gameComp);
        }
        WinStatus rewritten = winStatus;
        if (rewritten == WinStatus.TIME || rewritten == WinStatus.KILLERS) {
            rewritten = WinStatus.PASSENGERS;
        }
        if (rewritten == WinStatus.NONE && !MobRiotRules.hasLivingUnescapedInnocent(world, this, gameComp)) {
            rewritten = WinStatus.PASSENGERS;
        }
        return super.allowGameEnd(world, rewritten, looseEnds, gameComp);
    }

    public boolean isNight() {
        return this.cycleActive && this.phase == Phase.NIGHT;
    }

    public boolean isEscapeUnlocked() {
        return this.escapeUnlocked;
    }

    public boolean hasEscaped(UUID uuid) {
        return uuid != null && this.escaped.contains(uuid);
    }

    public int remainingPhaseTicks(ServerLevel world) {
        if (!this.cycleActive) {
            return 0;
        }
        return (int) Math.max(0L, this.phaseEndTick - GameUtils.getTicksFromGameStart(world));
    }

    public void applyPsychoDisguise(ServerPlayer player) {
        if (!this.cycleActive || this.phase != Phase.NIGHT) {
            return;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        SRERole role = game.getRole(player);
        if (!MobRiotRules.isKillerCamp(role)) {
            return;
        }
        ResourceLocation texture = role.getPsychoSkin(player, false);
        if (texture == null) {
            return;
        }
        MorphApi.morphToTexture(player, texture, false, Math.max(1, remainingPhaseTicks(player.serverLevel())));
    }

    private void beginPhase(ServerLevel world, Phase next) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        if (this.phase == Phase.NIGHT && next == Phase.DAY) {
            clearNightEffects(world);
        }
        this.phase = next;
        long now = GameUtils.getTicksFromGameStart(world);
        if (next == Phase.DAY) {
            world.setDayTime(MC_DAY_TIME);
            this.phaseEndTick = now + DAY_TICKS;
            this.nightGrantAtTick = -1L;
            this.nightRewardsGranted = true;
            grantInvisibility(world);
            grantDayRewards(world, game);
            broadcast(world, Component.translatable("message.sre.mob_riot.day_start").withStyle(ChatFormatting.GOLD));
        } else {
            removeStaminaBoosts(world);
            world.setDayTime(MC_NIGHT_TIME);
            this.phaseEndTick = now + NIGHT_TICKS;
            this.nightGrantAtTick = now + INVIS_TICKS;
            this.nightRewardsGranted = false;
            MeetingManager.cancelVotePhase();
            grantInvisibility(world);
            broadcast(world, Component.translatable("message.sre.mob_riot.night_start").withStyle(ChatFormatting.DARK_PURPLE));
        }
        syncToPlayers(world);
    }

    private void grantInvisibility(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVIS_TICKS, 0, true, false, true));
        }
    }

    private void grantDayRewards(ServerLevel world, SREGameWorldComponent game) {
        int staminaDuration = remainingPhaseTicks(world);
        for (ServerPlayer player : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            player.addEffect(new MobEffectInstance(ModEffects.STAMINA_RECOVERY, staminaDuration, 0, true, false, false));
            player.addEffect(new MobEffectInstance(ModEffects.STAMINA_BOOST, staminaDuration, 0, true, false, false));
            SRERole role = game.getRole(player);
            if (role == null || !role.isInnocent()) {
                continue;
            }
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop != null) {
                shop.addToBalance(DAY_GOLD);
            }
            SREPlayerMoodComponent mood = SREPlayerMoodComponent.KEY.get(player);
            if (mood != null) {
                mood.addMood(DAY_SAN);
                mood.sync();
            }
        }
    }

    private void grantNightRewards(ServerLevel world, SREGameWorldComponent game) {
        for (ServerPlayer player : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            SRERole role = game.getRole(player);
            if (!MobRiotRules.isKillerCamp(role)) {
                continue;
            }
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop != null) {
                shop.addToBalance(NIGHT_GOLD);
            }
            SREItemUtils.insertStackInFreeSlot(player, new ItemStack(TMMItems.MOB_PSYCHO_DISGUISE));
            applyPsychoDisguise(player);
            player.displayClientMessage(
                    Component.translatable("message.sre.mob_riot.disguise_granted").withStyle(ChatFormatting.RED),
                    true);
        }
    }

    private void removeStaminaBoosts(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            player.removeEffect(ModEffects.STAMINA_RECOVERY);
            player.removeEffect(ModEffects.STAMINA_BOOST);
        }
    }

    private void clearNightEffects(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            MorphApi.clearMorph(player);
            clearDisguiseItems(player);
        }
    }

    private void clearDisguiseItems(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(TMMItems.MOB_PSYCHO_DISGUISE)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void tryUnlock(ServerLevel world) {
        int goal = MobRiotRules.tokenGoal(this.startingPlayerCount);
        int tokens = MobRiotRules.sumTokens(world.getServer());
        if (tokens < goal) {
            return;
        }
        this.escapeUnlocked = true;
        broadcast(world, Component.translatable("message.sre.mob_riot.escape_unlocked", tokens, goal)
                .withStyle(ChatFormatting.GREEN));
        syncToPlayers(world);
    }

    private void tickEscapeSleep(ServerLevel world, SREGameWorldComponent game) {
        for (ServerPlayer player : world.players()) {
            UUID uuid = player.getUUID();
            if (this.escaped.contains(uuid) || !GameUtils.isPlayerAliveAndSurvival(player)) {
                this.sleepTicks.remove(uuid);
                continue;
            }
            SRERole role = game.getRole(player);
            if (!MobRiotRules.canSleepEscape(role) || !player.isSleeping() || !MobRiotRules.isInOwnRoom(player)) {
                this.sleepTicks.remove(uuid);
                continue;
            }
            int next = this.sleepTicks.getOrDefault(uuid, 0) + 1;
            this.sleepTicks.put(uuid, next);
            if (next >= SLEEP_CONFIRM_TICKS) {
                completeEscape(world, game, player);
            }
        }
    }

    private void completeEscape(ServerLevel world, SREGameWorldComponent game, ServerPlayer player) {
        this.escaped.add(player.getUUID());
        this.sleepTicks.remove(player.getUUID());
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        player.setGameMode(GameType.SPECTATOR);
        ServerPlayNetworking.send(player, new PlayerDeathPayload());
        grantEscapeBounty(world, game);
        broadcast(world, Component.translatable("message.sre.mob_riot.player_escaped", player.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
        syncToPlayers(world);
    }

    private void grantEscapeBounty(ServerLevel world, SREGameWorldComponent game) {
        for (ServerPlayer player : world.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            SRERole role = game.getRole(player);
            if (!MobRiotRules.isKillerOrKillerNeutral(role)) {
                continue;
            }
            SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
            if (shop != null) {
                shop.addToBalance(ESCAPE_GOLD);
            }
        }
    }

    private void broadcast(ServerLevel world, Component message) {
        for (ServerPlayer player : world.players()) {
            player.sendSystemMessage(message);
            player.displayClientMessage(message, true);
        }
    }

    private void syncToPlayers(ServerLevel world) {
        int tokens = world.getServer() == null ? 0 : MobRiotRules.sumTokens(world.getServer());
        int goal = MobRiotRules.tokenGoal(this.startingPlayerCount);
        int remaining = this.cycleActive ? Math.max(0, remainingPhaseTicks(world) / 20) : 0;
        MobRiotStateS2CPacket packet = new MobRiotStateS2CPacket(
                this.cycleActive,
                this.phase != Phase.NIGHT,
                remaining,
                tokens,
                goal,
                this.escapeUnlocked);
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private void resetState() {
        this.phase = Phase.INACTIVE;
        this.cycleActive = false;
        this.phaseEndTick = -1L;
        this.nightGrantAtTick = -1L;
        this.nightRewardsGranted = true;
        this.escapeUnlocked = false;
        this.startingPlayerCount = 0;
        this.escaped.clear();
        this.sleepTicks.clear();
    }

    @Override
    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        nbtCompound.putString("Phase", this.phase.name());
        nbtCompound.putBoolean("CycleActive", this.cycleActive);
        nbtCompound.putLong("PhaseEndTick", this.phaseEndTick);
        nbtCompound.putLong("NightGrantAtTick", this.nightGrantAtTick);
        nbtCompound.putBoolean("NightRewardsGranted", this.nightRewardsGranted);
        nbtCompound.putBoolean("EscapeUnlocked", this.escapeUnlocked);
        nbtCompound.putInt("StartingPlayerCount", this.startingPlayerCount);
        ListTag escapedTag = new ListTag();
        for (UUID uuid : this.escaped) {
            escapedTag.add(NbtUtils.createUUID(uuid));
        }
        nbtCompound.put("Escaped", escapedTag);
    }

    @Override
    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        try {
            this.phase = Phase.valueOf(nbtCompound.getString("Phase"));
        } catch (Exception ignored) {
            this.phase = Phase.INACTIVE;
        }
        this.cycleActive = nbtCompound.getBoolean("CycleActive");
        this.phaseEndTick = nbtCompound.contains("PhaseEndTick") ? nbtCompound.getLong("PhaseEndTick") : -1L;
        this.nightGrantAtTick = nbtCompound.contains("NightGrantAtTick") ? nbtCompound.getLong("NightGrantAtTick") : -1L;
        this.nightRewardsGranted = !nbtCompound.contains("NightRewardsGranted")
                || nbtCompound.getBoolean("NightRewardsGranted");
        this.escapeUnlocked = nbtCompound.getBoolean("EscapeUnlocked");
        this.startingPlayerCount = nbtCompound.getInt("StartingPlayerCount");
        this.escaped.clear();
        this.sleepTicks.clear();
        if (nbtCompound.contains("Escaped", Tag.TAG_LIST)) {
            for (Tag tag : nbtCompound.getList("Escaped", Tag.TAG_INT_ARRAY)) {
                this.escaped.add(NbtUtils.loadUUID(tag));
            }
        }
    }
}
