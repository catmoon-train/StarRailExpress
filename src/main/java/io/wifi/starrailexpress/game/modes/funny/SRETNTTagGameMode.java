package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SRETNTTagGameMode extends SREMurderGameMode {
    public SRETNTTagGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    public final static int roundGapTime = 10 * 20;
    public long nextBombTime = -1;
    public long nextRoundTime = -1;

    @Override
    public void writeToNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        nbtCompound.putLong("nextBomb", nextBombTime);
    }

    @Override
    public void readFromNbt(CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        if (nbtCompound.contains("nextBomb")) {
            this.nextBombTime = nbtCompound.getLong("nextBomb");
        } else {
            this.nextBombTime = -1;
        }
    }

    @Override
    public boolean onlyOneWinner() {
        return true;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        nextBombTime = -1;
        nextRoundTime = -1;
        super.initializeGame(serverWorld, gameWorldComponent, players);
    }

    /**
     * 尝试杀死玩家时触发（GameUtils.killPlayer传递）
     * 
     * @param victim      受害者
     * @param spawnBody   生成尸体
     * @param _killer     杀手（为空认为无杀手）
     * @param deathReason 死亡原因
     * @param forceDeath  强制死亡
     */
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        if (forceDeath) {
            super.killPlayer(victim, spawnBody, _killer, deathReason, true);
            return;
        }

        if (_killer != null) {
            if (transformTNTTag(_killer, victim))
                return;
        }
        GameUtils.teleportBackToRoom(victim);
        return;
    }

    public static boolean transformTNTTag(Player from_player, Player to_player) {
        Level level = from_player.level();
        var modifierComponent = WorldModifierComponent.KEY.get(level);
        if (!modifierComponent.isModifier(from_player, SpecialGameModeModifiers.TNT_TAGGED)
                || modifierComponent.isModifier(to_player, SpecialGameModeModifiers.TNT_TAGGED)) {
            return false;
        }
        SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.game.gamemode.tnttag.transform",
                        GameReplayUtils.getReplayPlayerDisplayText(from_player, true),
                        GameReplayUtils.getReplayPlayerDisplayText(to_player, true)));
        modifierComponent.removeModifier(from_player.getUUID(), SpecialGameModeModifiers.TNT_TAGGED);
        modifierComponent.addModifier(to_player.getUUID(), SpecialGameModeModifiers.TNT_TAGGED);
        MCItemsUtils.clearItem(from_player, FunnyItems.HOT_POTATO);
        MCItemsUtils.insertStackInFreeSlot(to_player, FunnyItems.HOT_POTATO.getDefaultInstance());
        return true;
    }

    public int getRoundTime(ServerLevel serverWorld, int player_size) {

        if (player_size <= 5) {
            return 45;
        }
        return 60;
    }

    public int getTagCounts(ServerLevel world, int player_size) {
        if (player_size <= 3) {
            return 1;
        }
        if (player_size <= 6) {
            return 2;
        }
        if (player_size <= 12) {
            return 3;
        }
        if (player_size <= 18) {
            return 4;
        }
        if (player_size <= 24) {
            return 5;
        }
        return (int) ((player_size / 6) + 1);
    }

    public void newRound(ServerLevel serverWorld) {
        long player_size = serverWorld.players().stream()
                .filter((p) -> GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)).count();
        int roundTime = getRoundTime(serverWorld, (int) player_size);
        nextRoundTime = 0;
        SREGameTimeComponent.KEY.get(serverWorld).setTime(roundTime);
        int tagCount = getTagCounts(serverWorld, roundTime);
        var players = new ArrayList<>(serverWorld.players());
        players.removeIf(p -> !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p));
        Collections.shuffle(players);
        MutableComponent allTaggedPlayersMessage = Component.literal("");
        var modifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        for (int i = 0; i < tagCount && i < players.size(); i++) {
            var p = players.get(i);
            modifierComponent.addModifier(p.getUUID(), SpecialGameModeModifiers.TNT_TAGGED, false);
            if (i > 0) {
                allTaggedPlayersMessage.append(", ");
            }
            allTaggedPlayersMessage.append(p.getName());
        }
        modifierComponent.sync();
        broadcastMessage(serverWorld,
                Component.translatable("gui.sre.gamemode.tnt_tag.new_round", allTaggedPlayersMessage)
                        .withStyle(ChatFormatting.GOLD),
                false);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        boolean haveSafeTime = false;
        int aliveCount = 0;
        ServerPlayer alivePlayer = null;
        for (ServerPlayer p : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                if (p.hasEffect(ModEffects.SAFE_TIME)) {
                    haveSafeTime = true;
                }
                alivePlayer = p;
                aliveCount++;
            }
        }
        if (haveSafeTime) {
            return;
        }
        if (aliveCount <= 1) {
            if (alivePlayer != null) {
                SREGameWorldComponent.KEY.get(serverWorld).setLooseEndWinner(alivePlayer.getUUID());
            }
            GameUtils.stopGame(serverWorld);
            return;
        }
        if (this.nextBombTime > 0 && serverWorld.getGameTime() >= this.nextBombTime) {
            tntBomb(serverWorld);
        } else if (this.nextBombTime <= 0 && this.nextRoundTime <= 0) {
            pendingNextRound(serverWorld);
        } else if (this.nextRoundTime > 0 && serverWorld.getGameTime() >= this.nextRoundTime) {
            newRound(serverWorld);
        }

        if (!serverWorld.isClientSide && serverWorld.getGameTime() % 80 == 0) {
            for (ServerPlayer sp : serverWorld.players()) {
                sp.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        (int) (100), // 持续时间（tick）
                        0, // 等级（0 = 速度 I）
                        false, // ambient（环境效果，如信标）
                        true, // showParticles（显示粒子）
                        false // showIcon（显示图标）
                ));
            }
        }
    }

    public void pendingNextRound(ServerLevel serverWorld) {
        this.nextBombTime = 0;
        this.nextRoundTime = serverWorld.getGameTime() + roundGapTime;
        SREGameTimeComponent.KEY.get(serverWorld).setTime(roundGapTime);
        broadcastMessage(serverWorld,
                Component.translatable("gui.sre.gamemode.tnt_tag.next_round_time", roundGapTime / 20)
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    public void broadcastMessage(ServerLevel serverWorld, Component message, boolean bomb) {
        for (ServerPlayer p : serverWorld.players()) {
            BroadcastCommand.BroadcastMessage(p, message);
            p.sendSystemMessage(message);
            if (bomb) {
                p.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.MASTER, 1f, 1f);
            } else {
                p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1f, 1f);
            }
        }
    }

    public void tntBomb(ServerLevel serverWorld) {
        this.nextBombTime = 0;
        this.nextRoundTime = 0;

        MutableComponent allTaggedPlayersMessage = Component.literal("");
        var players = new ArrayList<>(serverWorld.players());
        var modifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        players.removeIf((p) -> !GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)
                || !modifierComponent.isModifier(p, SpecialGameModeModifiers.TNT_TAGGED));
        for (int i = 0; i < players.size(); i++) {
            var p = players.get(i);
            modifierComponent.removeModifier(p.getUUID(), SpecialGameModeModifiers.TNT_TAGGED, false);
            GameUtils.killPlayer(p, true, p, SRE.id("bomb_death"), true);
            if (i > 0) {
                allTaggedPlayersMessage.append(", ");
            }
            allTaggedPlayersMessage.append(p.getName());
        }
        modifierComponent.sync();
        broadcastMessage(serverWorld,
                Component.translatable("gui.sre.gamemode.tnt_tag.bomb", allTaggedPlayersMessage)
                        .withStyle(ChatFormatting.GOLD),
                true);
        pendingNextRound(serverWorld);
    }

    @Override
    public boolean canAllPeopleSeeTime() {
        return true;
    }

    static {
        SRE.canSendReplay.add((p) -> {
            if (p == null)
                return false;
            if (SREGameWorldComponent.KEY.get(p.level()).getGameMode().identifier
                    .equals(SREGameModes.TNT_TAG_MODE_ID)) {
                return true;
            }
            return false;
        });
        SRE.canUseChatHudPlayer.add((p) -> {
            if (p == null)
                return false;
            if (SREGameWorldComponent.KEY.get(p.level()).getGameMode().identifier
                    .equals(SREGameModes.TNT_TAG_MODE_ID)) {
                return true;
            }
            return false;
        });
    }

    @Override
    public void tickClientGameLoop(Level level) {
        if (SREClient.cached_player != null && level.getGameTime() % 20 == 0 && this.nextBombTime > 0) {
            boolean isTagged = SREClient.modifierComponent.isModifier(SREClient.cached_player,
                    SpecialGameModeModifiers.TNT_TAGGED);
            long remainingTime = (this.nextBombTime - level.getGameTime());
            if (isTagged) {

                SREClient.cached_player.displayClientMessage(
                        Component.translatable("gui.sre.gamemode.tnt_tag.bomb_time_having", remainingTime / 20)
                                .withStyle(ChatFormatting.BOLD, ChatFormatting.RED),
                        true);
            } else {
                SREClient.cached_player.displayClientMessage(
                        Component.translatable("gui.sre.gamemode.tnt_tag.bomb_time", remainingTime / 20)
                                .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                        true);
            }
        }
    }
}
