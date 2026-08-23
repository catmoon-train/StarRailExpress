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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class RecorderRoleData extends SimpleRoleData {




    private List<ResourceLocation> availableRoles = new ArrayList<>();

    private Map<UUID, ResourceLocation> guesses = new HashMap<>();

    private Map<UUID, String> startPlayers = new HashMap<>();

    private int wrongGuessCount = 0;
    // private int MAX_WRONG_GUESSES = 10;
    private int MAX_WRONG_GUESSES = 8;
    private boolean rolesInitialized = false;
    // private boolean wasRunning = false;
    public int requiredCorrectCount = 0;

    public RecorderRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        this.guesses.clear();
        this.availableRoles.clear();
        this.startPlayers.clear();
        this.wrongGuessCount = 0;
        this.rolesInitialized = false;
        this.MAX_WRONG_GUESSES = 5;
        sync();
    }

    public void initRecorder() {
        int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
        this.requiredCorrectCount = getRequiredCorrectCount(totalPlayers);
        this.MAX_WRONG_GUESSES = 5;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public List<ResourceLocation> getAvailableRoles() {
        return availableRoles;
    }

    public boolean hasGuessed(UUID playerUUID) {
        return this.guesses.containsKey(playerUUID) && this.guesses.get(playerUUID) != null;
    }

    public Map<UUID, String> getStartPlayers() {
        return startPlayers;
    }

    public void setAvailableRoles(List<ResourceLocation> roles) {
        this.availableRoles = roles;
        this.rolesInitialized = true;
        // sync();
    }

    public void addGuess(UUID targetUuid, ResourceLocation roleId) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (guesses.containsKey(targetUuid)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.recorder.already_guessed")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        Player target = player.level().getPlayerByUUID(targetUuid);
        boolean isCorrect = false;

        int playerCount = gameWorld.getPlayerCount();
        int cooldownSeconds;
        if (playerCount < 10) {
            cooldownSeconds = 1;
        } else if (playerCount < 15) {
            cooldownSeconds = 1;
        } else if (playerCount < 20) {
            cooldownSeconds = 1;
        } else if (playerCount < 30) {
            cooldownSeconds = 1;
        } else {
            cooldownSeconds = 1;
        }
        serverPlayer.getCooldowns().addCooldown(ModItems.WRITTEN_NOTE, cooldownSeconds * 20);
        if (target != null) {
            SRERole actualRole = gameWorld.getRole(target);
            if (actualRole != null && actualRole.identifier().equals(roleId)) {
                isCorrect = true;
            }
        }

        if (isCorrect) {
            guesses.put(targetUuid, roleId);
            sync();
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.recorder.correct_guess")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            // 回放记录：记录员使用记录笔记记录了某玩家身份
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.recorder.record_identity",
                            GameReplayUtils.getReplayPlayerDisplayText(serverPlayer, true),
                            GameReplayUtils.getReplayPlayerDisplayText(target, true)));
            checkWinCondition();
        } else {
            
            // 回放记录：记录员使用记录笔记记录了某玩家身份
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.recorder.record_identity.wrong",
                            GameReplayUtils.getReplayPlayerDisplayText(serverPlayer, true),
                            GameReplayUtils.getReplayPlayerDisplayText(target, true)));

            wrongGuessCount++;
            serverPlayer.displayClientMessage(
                    Component
                            .translatable("message.noellesroles.recorder.wrong_guess", wrongGuessCount,
                                    MAX_WRONG_GUESSES)
                            .withStyle(ChatFormatting.RED),
                    true);

            if (wrongGuessCount >= MAX_WRONG_GUESSES) {
                // 猜错10次，立刻死亡
                GameUtils.killPlayer(player, true, null, Noellesroles.id("recorder_mistake"));
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.recorder.died_from_mistakes")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        false);
            }
        }
    }

    /** 重置错误猜测计数（领袖追随者效果：免疫记录错误死亡） */
    public void resetWrongGuesses() {
        this.wrongGuessCount = 0;
        sync();
    }

    public Map<UUID, ResourceLocation> getGuesses() {
        return guesses;
    }

    public int getCorrectGuesses() {
        return guesses.size();
    }

    private void checkWinCondition() {
        if (!(player instanceof ServerPlayer))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        int correctGuesses = guesses.size();

        var players = player.level().players();
        int totalPlayers = 0;
        for (Player pplayer : players) {
            if (gameWorld.getRole(pplayer) != null) {
                totalPlayers++;
            }
        }
        int requiredCorrect = this.requiredCorrectCount;

        if (requiredCorrect < 2)
            requiredCorrect = 2;
        if (requiredCorrect > totalPlayers - 1)
            requiredCorrect = totalPlayers - 1;
        if (this.requiredCorrectCount != requiredCorrect) {
            this.requiredCorrectCount = requiredCorrect;
            this.sync();
        }
        if (correctGuesses >= requiredCorrect) {
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                // 补充 CustomWinnerID: recorder
                RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.RECORDER, "recorder", null);
            }

            // 广播胜利消息
            for (Player p : player.level().players()) {
                p.displayClientMessage(
                        Component.translatable("message.noellesroles.recorder.win", player.getName())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        true);
            }
        }
    }

    public int getRequiredCorrectCount(int totalPlayers) {
        if (totalPlayers <= 8)
            return 5;
        if (totalPlayers <= 12) {
            return (int) 6;
        }
        if (totalPlayers <= 18) {
            return 7;
        }
        if (totalPlayers <= 24) {
            return 8;
        }
        if (totalPlayers <= 27)
            return 9;
        if (totalPlayers <= 32)
            return 10;
        return totalPlayers / 3;
    }

    @Override
    public void serverTick() {
        if (!rolesInitialized && player instanceof ServerPlayer) {
            initializeRoles();
        }
    }

    public void initializeRoles() {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel))
            return;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.RECORDER))
            return;

        // 初始化开局玩家列表（仅在为空时初始化）
        if (startPlayers.isEmpty()) {
            for (Player p : player.level().players()) {
                if (p.getUUID().equals(player.getUUID()))
                    continue;
                startPlayers.put(p.getUUID(), p.getName().getString());
            }
            sync();
        }

        updateAvailableRoles();
    }


    public void updateAvailableRoles() {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel))
            return;

    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // availableRoles.clear();
        // if (tag.contains("availableRoles")) {
        // ListTag list = tag.getList("availableRoles", Tag.TAG_STRING);
        // for (int i = 0; i < list.size(); i++) {
        // availableRoles.add(ResourceLocation.tryParse(list.getString(i)));
        // }
        // }
        requiredCorrectCount = tag.contains("requiredCorrectCount") ? tag.getInt("requiredCorrectCount") : 10;

        guesses.clear();
        if (tag.contains("guesses")) {
            CompoundTag guessesTag = tag.getCompound("guesses");
            for (String key : guessesTag.getAllKeys()) {
                try {
                    UUID targetUuid = UUID.fromString(key);
                    ResourceLocation roleId = ResourceLocation.tryParse(guessesTag.getString(key));
                    if (roleId != null) {
                        guesses.put(targetUuid, roleId);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        startPlayers.clear();
        if (tag.contains("startPlayers")) {
            CompoundTag startPlayersTag = tag.getCompound("startPlayers");
            for (String key : startPlayersTag.getAllKeys()) {
                try {
                    UUID targetUuid = UUID.fromString(key);
                    String name = startPlayersTag.getString(key);
                    startPlayers.put(targetUuid, name);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        MAX_WRONG_GUESSES = tag.getInt("MAX_WRONG_GUESSES");
        if (MAX_WRONG_GUESSES <= 5)
            MAX_WRONG_GUESSES = 5;
        wrongGuessCount = tag.getInt("wrongGuessCount");
        rolesInitialized = tag.getBoolean("rolesInitialized");
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // ListTag list = new ListTag();
        // for (ResourceLocation id : availableRoles) {
        // if (id != null) {
        // list.add(StringTag.valueOf(id.toString()));
        // }
        // }
        // tag.put("availableRoles", list);

        CompoundTag guessesTag = new CompoundTag();
        for (Map.Entry<UUID, ResourceLocation> entry : guesses.entrySet()) {
            if (entry.getValue() != null) {
                guessesTag.putString(entry.getKey().toString(), entry.getValue().toString());
            }
        }
        tag.put("guesses", guessesTag);

        CompoundTag startPlayersTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : startPlayers.entrySet()) {
            startPlayersTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("startPlayers", startPlayersTag);

        tag.putInt("wrongGuessCount", wrongGuessCount);
        tag.putInt("MAX_WRONG_GUESSES", MAX_WRONG_GUESSES);
        tag.putInt("requiredCorrectCount", requiredCorrectCount);
        tag.putBoolean("rolesInitialized", rolesInitialized);

    }



}
