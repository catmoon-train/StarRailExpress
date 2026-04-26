package org.agmas.noellesroles.game.modes.fourthroom.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlockEntity;
import org.agmas.noellesroles.game.modes.fourthroom.card.*;
import org.agmas.noellesroles.game.modes.fourthroom.config.FourthRoomConfig;
import org.agmas.noellesroles.game.modes.fourthroom.duel.FourthRoomDuelManager;
import org.agmas.noellesroles.game.modes.fourthroom.effect.EffectEvent;
import org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents;
import org.agmas.noellesroles.game.modes.fourthroom.network.FourthRoomStatePayload;
import org.agmas.noellesroles.game.modes.fourthroom.network.OpenFourthRoomPeekDeckPayload;
import org.agmas.noellesroles.game.modes.fourthroom.room.RoomDefinition;
import org.agmas.noellesroles.game.modes.fourthroom.room.RoomManager;
import org.agmas.noellesroles.game.modes.fourthroom.shop.FourthRoomShopItem;
import org.agmas.noellesroles.game.modes.fourthroom.shop.FourthRoomShopService;
import org.agmas.noellesroles.game.modes.fourthroom.task.FourthRoomTaskScheduler;
import org.agmas.noellesroles.game.modes.fourthroom.task.FourthRoomTaskType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class FourthRoomGameManager {
    private final ServerLevel level;
    private final FourthRoomSavedData data;
    private final FourthRoomConfig config;
    private final RoomManager roomManager;
    private final FourthRoomTaskScheduler taskScheduler;
    private final FourthRoomShopService shopService;
    private final FourthRoomDuelManager duelManager;

    public FourthRoomGameManager(ServerLevel level) {
        this.level = level;
        this.data = FourthRoomSavedData.get(level);
        this.config = FourthRoomConfig.get();
        this.roomManager = new RoomManager(level, data, config);
        this.taskScheduler = new FourthRoomTaskScheduler(this, data, config);
        this.shopService = new FourthRoomShopService(this, data, config);
        this.duelManager = new FourthRoomDuelManager(this, data, config);
    }

    public static FourthRoomGameManager of(ServerLevel level) {
        return new FourthRoomGameManager(level);
    }

    public static void setRequestedPlayerCount(ServerLevel level, int playerCount) {
        FourthRoomSavedData savedData = FourthRoomSavedData.get(level);
        savedData.requestedPlayerCount = Math.max(2, playerCount);
        savedData.setDirty(true);
    }

    public void initializeMatch(List<ServerPlayer> readyPlayers) {
        List<ServerPlayer> participants = new ArrayList<>(readyPlayers);
        Collections.shuffle(participants);
        int requested = Math.max(2,
                data.requestedPlayerCount > 0 ? data.requestedPlayerCount : config.defaultPlayerCount);
        if (participants.size() > requested) {
            participants = new ArrayList<>(participants.subList(0, requested));
        }
        if ((participants.size() & 1) == 1) {
            participants.removeLast();
        }
        data.resetMatchState();
        data.requestedPlayerCount = Math.max(2, requested);
        if (participants.size() < 2) {
            broadcast("Fourth Room requires at least two ready players.");
            return;
        }
        data.active = true;
        data.phase = FourthRoomPhase.CARD_BATTLE;
        data.startedGameTick = currentTick();
        assignTeamsAndIdentities(participants);
        roomManager.assignRooms(new ArrayList<>(data.players.values()));
        roomManager.teleportPlayersToRooms();
        data.nextRotationTick = currentTick() + config.rotationIntervalSeconds * 20L;
        taskScheduler.scheduleNextTask(currentTick());
        data.setDirty(true);
        broadcast("Fourth Room match started with " + participants.size() + " players.");
        syncMatchState();
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            emitActiveTurnFocus(roomState.roomId, roomState.activePlayerId, 60L);
        }
    }

    public void shutdownMatch() {
        List<ServerPlayer> recipients = new ArrayList<>();
        for (FourthRoomPlayerState playerState : data.players.values()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerState.playerId);
            if (player != null) {
                recipients.add(player);
            }
        }
        data.resetMatchState();
        clearRoomTables();
        for (ServerPlayer player : recipients) {
            FourthRoomStatePayload.send(player, buildSnapshot(player).toString());
        }
    }

    public void tickServer() {
        if (!data.active) {
            return;
        }
        processPoisonDeaths();
        if (data.phase == FourthRoomPhase.ROTATING && currentTick() >= data.rotationResumeTick) {
            finishRotation();
        }
        if (data.phase == FourthRoomPhase.CARD_BATTLE) {
            taskScheduler.tick();
            if (currentTick() >= data.nextRotationTick) {
                startRotation();
            }
        }
        if (data.phase == FourthRoomPhase.DUEL) {
            duelManager.tick();
        }
        duelManager.maybeResolveWinCondition();
    }

    public boolean playCard(UUID playerId, String cardId, UUID targetId) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null || !playerState.alive) {
            return false;
        }
        for (int handIndex = 0; handIndex < playerState.hand.size(); handIndex++) {
            if (playerState.hand.get(handIndex).cardId().equals(cardId)) {
                return playCardByHandIndex(playerId, handIndex, targetId);
            }
        }
        return false;
    }

    public boolean playCardByHandIndex(UUID playerId, int handIndex, @Nullable UUID targetId) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null || !playerState.alive || handIndex < 0 || handIndex >= playerState.hand.size()) {
            return false;
        }
        CardInstance instance = playerState.hand.get(handIndex);
        Card card = CardRegistry.byId(instance.cardId());
        if (card == null) {
            return false;
        }
        if (!card.isSkill() && !isPlayersTurn(playerId)) {
            return false;
        }
        boolean requiresTarget = cardRequiresTarget(card, instance.gold());
        List<UUID> validTargets = validCardTargets(playerId, instance);
        UUID resolvedTarget = targetId;
        if (requiresTarget) {
            if (resolvedTarget == null) {
                if (validTargets.size() == 1) {
                    resolvedTarget = validTargets.getFirst();
                } else {
                    return false;
                }
            }
            if (!validTargets.contains(resolvedTarget)) {
                return false;
            }
        } else {
            if (resolvedTarget == null || !validTargets.contains(resolvedTarget)) {
                resolvedTarget = roomManager.getOpponent(playerId);
            }
        }
        if (card.id().equals("point_kill") && !instance.gold()) {
            resolvedTarget = playerId;
        }
        playerState.hand.remove(handIndex);
        boolean success = card.play(this, playerId, resolvedTarget, instance);
        if (!success) {
            playerState.hand.add(handIndex, instance);
            return false;
        }
        playerState.discardPile.add(instance);
        logPlayerRoomAction(playerId,
                card.isSkill() ? "skill" : "card",
                "使用了",
                cardDisplayName(card),
                playerName(resolvedTarget),
                instance.gold() ? "金卡" : "");
        emitCardPlayEffects(playerState.roomId, playerId, cardDisplayName(card), instance.gold(), card.isSkill());
        // Add the played card to the table display
        FourthRoomTableBlockEntity table = getRoomTable(playerState.roomId, roomManager.buildRoomDefinitions());
        if (table != null) {
            int color = instance.gold() ? 0xFFF2C56A : (card.isSkill() ? 0xFF6FD6C5 : 0xFFF08B55);
            table.addTableCard(cardDisplayName(card), playerName(playerId) + " 使用", color, instance.gold());
        }
        data.setDirty(true);
        syncMatchState();
        return true;
    }

    public boolean cardRequiresTarget(CardInstance instance) {
        Card card = CardRegistry.byId(instance.cardId());
        return card != null && cardRequiresTarget(card, instance.gold());
    }

    public List<UUID> validCardTargets(UUID playerId, CardInstance instance) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null) {
            return List.of();
        }
        FourthRoomRoomState roomState = data.rooms.get(playerState.roomId);
        Card card = CardRegistry.byId(instance.cardId());
        if (roomState == null || card == null) {
            return List.of();
        }
        boolean allowSelf = "veto".equals(card.id()) || "point_kill".equals(card.id());
        List<UUID> targets = new ArrayList<>();
        for (UUID occupantId : roomState.occupants) {
            FourthRoomPlayerState occupant = data.players.get(occupantId);
            if (occupant == null || !occupant.alive) {
                continue;
            }
            if (!allowSelf && occupantId.equals(playerId)) {
                continue;
            }
            targets.add(occupantId);
        }
        return targets;
    }

    public boolean canEndTurn(UUID playerId) {
        return isPlayersTurn(playerId);
    }

    public boolean canRevealOwnIdentity(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        return state != null && state.alive && state.firstHiddenIdentityIndex() >= 0;
    }

    public boolean endTurn(UUID playerId) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null || !isPlayersTurn(playerId) || !roomManager.hasLivingOpponent(playerId)) {
            if (playerState != null) {
                roomManager.refreshRoomTurnState(playerState.roomId);
            }
            return false;
        }
        if (playerState.extraTurns > 0) {
            playerState.extraTurns--;
            drawCards(playerId, 1, false);
            logPlayerRoomAction(playerId, "system", "额外回合结束", "", "", "剩余额外回合 " + playerState.extraTurns + " 摸 1 张牌");
            data.setDirty(true);
            syncMatchState();
            return true;
        }
        playerState.peekCache.clear();
        drawCards(playerId, 1, false);
        logPlayerRoomAction(playerId, "system", "结束了回合", "", "", "摸 1 张牌");
        roomManager.advanceTurn(playerState.roomId);
        resolveTurnEntry(playerState.roomId);
        FourthRoomRoomState roomState = data.rooms.get(playerState.roomId);
        emitActiveTurnFocus(playerState.roomId, roomState != null ? roomState.activePlayerId : null, 140L);
        syncMatchState();
        return true;
    }

    public boolean revealOwnIdentity(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || !state.alive) {
            return false;
        }
        int index = state.firstHiddenIdentityIndex();
        if (index < 0) {
            return false;
        }
        state.revealed.set(index, true);
        grantCoins(playerId, 2, "self_reveal");
        logPlayerRoomAction(playerId, "reveal", "翻开了身份", shortBlockId(state.identityBlocks.get(index)), "", "+2 金币");
        broadcastReveal(playerId, state.identityBlocks.get(index));
        data.setDirty(true);
        syncMatchState();
        return true;
    }

    public void drawCards(UUID playerId, int amount, boolean fromBottom) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || !state.alive) {
            return;
        }
        List<String> drawnCards = new ArrayList<>();
        for (int drawn = 0; drawn < amount; drawn++) {
            if (state.drawPile.isEmpty()) {
                shuffleDiscardIntoDeck(playerId);
            }
            if (state.drawPile.isEmpty()) {
                return;
            }
            CardInstance instance = fromBottom ? state.drawPile.removeFirst() : state.drawPile.removeLast();
            drawnCards.add(formatDrawnCardName(instance));
            if (instance.gold()) {
                grantCoins(playerId, 1, "gold_card");
            }
            Card card = CardRegistry.byId(instance.cardId());
            if (card == null) {
                continue;
            }
            if (card.isInstantOnDraw()) {
                card.onDraw(this, playerId, instance);
                state.discardPile.add(instance);
            } else {
                state.hand.add(instance);
            }
        }
        if (!drawnCards.isEmpty()) {
            state.drawNoticeSequence++;
            state.lastDrawSummary = String.join("、", drawnCards);
            emitDrawEffects(state.roomId, playerId, amount);
        }
        data.setDirty(true);
    }

    public void shuffleDiscardIntoDeck(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || state.discardPile.isEmpty()) {
            return;
        }
        // 将弃牌堆的牌洗匀后放到抽牌堆的底部
        List<CardInstance> shuffledDiscard = new ArrayList<>(state.discardPile);
        Collections.shuffle(shuffledDiscard);
        state.drawPile.addAll(0, shuffledDiscard);
        state.discardPile.clear();
        data.setDirty(true);
    }

    public boolean stealRandomCard(UUID playerId, UUID targetId) {
        UUID resolvedTarget = targetId != null ? targetId : roomManager.getOpponent(playerId);
        FourthRoomPlayerState targetState = data.players.get(resolvedTarget);
        FourthRoomPlayerState actorState = data.players.get(playerId);
        if (actorState == null || targetState == null) {
            return false;
        }
        List<CardInstance> eligible = targetState.hand.stream().filter(card -> {
            Card definition = CardRegistry.byId(card.cardId());
            return definition != null && definition.canBeStolenOrDismantled();
        }).toList();
        if (eligible.isEmpty()) {
            return false;
        }
        CardInstance stolen = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        targetState.hand.remove(stolen);
        actorState.hand.add(stolen);
        data.setDirty(true);
        return true;
    }

    public boolean dismantleRandomCard(UUID targetId) {
        FourthRoomPlayerState targetState = data.players.get(targetId);
        if (targetState == null) {
            return false;
        }
        List<CardInstance> eligible = targetState.hand.stream().filter(card -> {
            Card definition = CardRegistry.byId(card.cardId());
            return definition != null && definition.canBeStolenOrDismantled();
        }).toList();
        if (eligible.isEmpty()) {
            return false;
        }
        CardInstance dismantled = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        targetState.hand.remove(dismantled);
        targetState.drawPile.addFirst(dismantled);
        data.setDirty(true);
        return true;
    }

    public boolean interrogateOpponent(UUID playerId, UUID targetId) {
        UUID resolvedTarget = targetId != null ? targetId : roomManager.getOpponent(playerId);
        FourthRoomPlayerState targetState = data.players.get(resolvedTarget);
        if (targetState == null) {
            return false;
        }
        CardInstance selected = targetState.hand.stream().filter(card -> {
            Card definition = CardRegistry.byId(card.cardId());
            return definition != null && definition.canBeStolenOrDismantled();
        }).findFirst().orElse(null);
        if (selected == null) {
            return false;
        }
        targetState.hand.remove(selected);
        targetState.drawPile.addFirst(selected);
        data.setDirty(true);
        return true;
    }

    public boolean copyRandomCard(UUID playerId, UUID targetId) {
        UUID resolvedTarget = targetId != null ? targetId : roomManager.getOpponent(playerId);
        FourthRoomPlayerState targetState = data.players.get(resolvedTarget);
        FourthRoomPlayerState actorState = data.players.get(playerId);
        if (actorState == null || targetState == null) {
            return false;
        }
        // 只复制非技能牌（可以被偷取或拆解的牌）
        List<CardInstance> eligible = targetState.hand.stream().filter(card -> {
            Card definition = CardRegistry.byId(card.cardId());
            return definition != null && definition.canBeStolenOrDismantled();
        }).toList();
        if (eligible.isEmpty()) {
            return false;
        }
        CardInstance copied = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        // 创建一个新的卡牌实例（复制），保留金卡属性
        CardInstance newCopy = new CardInstance(copied.cardId(), copied.gold());
        actorState.hand.add(newCopy);
        data.setDirty(true);
        return true;
    }

    public void armDecoy(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state != null) {
            state.decoyArmed = true;
            data.setDirty(true);
        }
    }

    public boolean restoreOneIdentity(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return false;
        }
        for (int index = state.revealed.size() - 1; index >= 0; index--) {
            if (state.revealed.get(index)) {
                state.revealed.set(index, false);
                data.setDirty(true);
                return true;
            }
        }
        return false;
    }

    public boolean addMarkedKill(UUID targetId, int amount) {
        FourthRoomPlayerState state = data.players.get(targetId);
        if (state == null) {
            return false;
        }
        state.markedForKill += amount;
        data.setDirty(true);
        return true;
    }

    public void reduceMarkedKill(UUID playerId, int amount) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return;
        }
        state.markedForKill = Math.max(0, state.markedForKill - amount);
        data.setDirty(true);
    }

    public void addSkipTurns(UUID playerId, int amount) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state != null) {
            state.skipTurns += amount;
            data.setDirty(true);
        }
    }

    public void addExtraTurns(UUID playerId, int amount) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state != null) {
            state.extraTurns += amount;
            data.setDirty(true);
        }
    }

    public void addLifeShield(UUID playerId, int amount) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state != null) {
            state.lifeShield += amount;
            data.setDirty(true);
        }
    }

    public void peekTopCards(UUID playerId, int amount) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return;
        }
        state.peekCache.clear();
        for (int index = Math.max(0, state.drawPile.size() - amount); index < state.drawPile.size(); index++) {
            state.peekCache.add(state.drawPile.get(index).cardId());
        }
        data.setDirty(true);
        syncMatchState();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            OpenFourthRoomPeekDeckPayload.send(player);
        }
    }

    public void inflictCardDamage(UUID sourceId, UUID targetId, String reason) {
        FourthRoomPlayerState targetState = data.players.get(targetId);
        if (targetState == null || !targetState.alive) {
            return;
        }
        
        // 检查是否是死亡牌触发的伤害，如果是，检查玩家是否有命格卡
        if ("death_card".equals(reason)) {
            CardInstance lifeCard = targetState.hand.stream()
                    .filter(card -> "life".equals(card.cardId()))
                    .findFirst()
                    .orElse(null);
            
            if (lifeCard != null) {
                // 自动使用命格卡抵挡伤害
                targetState.hand.remove(lifeCard);
                targetState.discardPile.add(lifeCard);
                sendPrivate(targetId, Component.translatable("message.fourth_room.life_card_auto_used"));
                logRoomAction(targetState.roomId, "defense", playerName(targetId), "自动使用了", "命格", "", "抵挡了死亡牌伤害");
                data.setDirty(true);
                syncMatchState();
                return;
            }
        }
        
        if (targetState.lifeShield > 0) {
            targetState.lifeShield--;
            sendPrivate(targetId, Component.translatable("message.fourth_room.life_card_blocked"));
            logRoomAction(targetState.roomId, "defense", playerName(targetId), "抵挡了伤害", "命格", "", "");
            data.setDirty(true);
            return;
        }
        if (targetState.decoyArmed) {
            targetState.decoyArmed = false;
            UUID opponent = roomManager.getOpponent(targetId);
            if (opponent != null) {
                logRoomAction(targetState.roomId, "defense", playerName(targetId), "发动了", "诱饵", playerName(opponent),
                        "转移伤害");
                inflictCardDamage(sourceId, opponent, "decoy:" + reason);
                data.setDirty(true);
                return;
            }
        }
        int hiddenIdentityIndex = targetState.firstHiddenIdentityIndex();
        if (hiddenIdentityIndex >= 0) {
            targetState.revealed.set(hiddenIdentityIndex, true);
            logRoomAction(targetState.roomId, "damage", playerName(targetId), "被打翻了一块身份",
                    shortBlockId(targetState.identityBlocks.get(hiddenIdentityIndex)), "", "");
            broadcastReveal(targetId, targetState.identityBlocks.get(hiddenIdentityIndex));
        } else {
            eliminatePlayer(targetId, reason);
        }
        data.setDirty(true);
    }

    public void eliminatePlayer(UUID playerId, String reason) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || !state.alive) {
            return;
        }
        state.alive = false;
        logRoomAction(state.roomId, "damage", playerName(playerId), "被淘汰", reasonDisplay(reason), "", "");
        roomManager.refreshRoomTurnState(state.roomId);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.stopRiding();
            player.setGameMode(GameType.SPECTATOR);
            player.displayClientMessage(
                    Component.literal("You were eliminated by " + reason).withStyle(ChatFormatting.RED), false);
        }
        data.setDirty(true);
        duelManager.maybeResolveWinCondition();
    }

    public boolean buyItem(UUID playerId, FourthRoomShopItem item) {
        boolean success = shopService.buy(playerId, item);
        if (success) {
            sendPrivate(playerId, Component.translatable("message.fourth_room.purchased", item.id()));
        }
        return success;
    }

    public boolean useAssassinationItem(UUID attackerId, UUID targetId, FourthRoomShopItem item) {
        boolean success = shopService.useAssassinationItem(attackerId, targetId, item);
        if (success) {
            syncMatchState();
        }
        return success;
    }

    public boolean placeStickyNote(UUID playerId, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face,
            String text) {
        return shopService.placeStickyNote(playerId, pos, face, text);
    }

    public List<String> searchNotes(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return List.of();
        }
        return shopService.searchNotes(playerId, state.roomId);
    }

    public void grantCoins(UUID playerId, int baseAmount, String reason) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return;
        }
        int amount = Math.max(0, (int) Math.round(baseAmount * config.goldMultiplier));
        state.coins += amount;
        data.setDirty(true);
        if (amount > 0) {
            sendPrivate(playerId, Component.translatable("message.fourth_room.gold_change", amount, reason));
        }
    }

    public void broadcast(String message) {
        broadcast(Component.literal(message));
    }

    public void broadcast(Component message) {
        Component component = Component.translatable("fourth_room.prefix").append(message)
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(component, false);
        }
    }

    public void sendPrivate(UUID playerId, Component message) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("fourth_room.prefix").append(message).withStyle(ChatFormatting.YELLOW),
                    true);
        }
    }

    public void syncMatchState() {
        data.setDirty(true);
        syncRoomTables();
        for (FourthRoomPlayerState playerState : data.players.values()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerState.playerId);
            if (player != null) {
                FourthRoomStatePayload.send(player);
            }
        }
    }

    public JsonObject buildSnapshot(ServerPlayer viewer) {
        JsonObject root = new JsonObject();
        root.addProperty("active", data.active);
        root.addProperty("phase", data.phase.name());
        root.addProperty("phaseDisplayName", phaseDisplayName(data.phase));
        root.addProperty("serverTick", currentTick());
        root.addProperty("rotationCount", data.rotationCount);
        root.addProperty("nextRotationTick", data.nextRotationTick);
        root.addProperty("rotationIntervalTicks", config.rotationIntervalSeconds * 20L);
        root.addProperty("activeTaskId", data.activeTaskId == null ? "" : data.activeTaskId);
        FourthRoomTaskType activeTask = FourthRoomTaskType.byId(data.activeTaskId);
        root.addProperty("activeTaskDescription", activeTask != null ? activeTask.descriptionKey() : "");
        root.addProperty("hasActiveTask", taskScheduler.hasActiveTask());
        root.addProperty("taskDeadlineTick", data.taskDeadlineTick);
        root.addProperty("taskDurationTicks", config.taskDurationSeconds * 20L);
        if (data.winner != null) {
            root.addProperty("winner", data.winner.name());
            root.addProperty("winnerDisplayName", teamDisplayName(data.winner));
        }
        JsonArray roomPlayers = new JsonArray();
        root.add("roomPlayers", roomPlayers);
        JsonArray roomActions = new JsonArray();
        root.add("roomActions", roomActions);

        FourthRoomPlayerState state = data.players.get(viewer.getUUID());
        if (state == null) {
            return root;
        }

        JsonObject viewerJson = new JsonObject();
        viewerJson.addProperty("uuid", state.playerId.toString());
        viewerJson.addProperty("name", playerName(state.playerId));
        viewerJson.addProperty("alive", state.alive);
        viewerJson.addProperty("team", state.team.name());
        viewerJson.addProperty("teamDisplayName", teamDisplayName(state.team));
        viewerJson.addProperty("roomId", state.roomId);
        viewerJson.addProperty("coins", state.coins);
        viewerJson.addProperty("hiddenIdentityCount", state.hiddenIdentityCount());
        viewerJson.addProperty("taskCompleted", state.taskCompleted);
        viewerJson.addProperty("yourTurn", isPlayersTurn(state.playerId));
        viewerJson.addProperty("lifeShield", state.lifeShield);
        viewerJson.addProperty("skipTurns", state.skipTurns);
        viewerJson.addProperty("extraTurns", state.extraTurns);
        viewerJson.addProperty("markedForKill", state.markedForKill);
        viewerJson.addProperty("canReveal", state.firstHiddenIdentityIndex() >= 0);
        viewerJson.addProperty("canEndTurn", isPlayersTurn(state.playerId));
        viewerJson.addProperty("recentDrawSequence", state.drawNoticeSequence);
        viewerJson.addProperty("recentDrawSummary", state.lastDrawSummary == null ? "" : state.lastDrawSummary);
        viewerJson.addProperty("drawPileSize", state.drawPile.size());

        JsonArray identities = new JsonArray();
        for (int index = 0; index < state.identityBlocks.size(); index++) {
            JsonObject identity = new JsonObject();
            identity.addProperty("blockId", state.identityBlocks.get(index));
            identity.addProperty("revealed", state.revealed.get(index));
            identities.add(identity);
        }
        viewerJson.add("identities", identities);

        JsonArray hand = new JsonArray();
        for (CardInstance cardInstance : state.hand) {
            Card definition = CardRegistry.byId(cardInstance.cardId());
            JsonObject cardJson = new JsonObject();
            cardJson.addProperty("id", cardInstance.cardId());
            cardJson.addProperty("gold", cardInstance.gold());
            cardJson.addProperty("displayName", cardDisplayName(cardInstance.cardId()));
            cardJson.addProperty("description",
                    definition != null ? cardDescription(definition) : cardInstance.cardId());
            cardJson.addProperty("requiresTarget",
                    definition != null && cardRequiresTarget(definition, cardInstance.gold()));
            cardJson.addProperty("skill", definition != null && definition.isSkill());
            hand.add(cardJson);
        }
        viewerJson.add("hand", hand);

        JsonArray peek = new JsonArray();
        for (String cardId : state.peekCache) {
            JsonObject peekJson = new JsonObject();
            Card definition = CardRegistry.byId(cardId);
            peekJson.addProperty("id", cardId);
            peekJson.addProperty("displayName", cardDisplayName(cardId));
            peekJson.addProperty("description", definition != null ? cardDescription(definition) : cardId);
            peekJson.addProperty("skill", definition != null && definition.isSkill());
            peek.add(peekJson);
        }
        viewerJson.add("peekCache", peek);

        JsonArray shopItems = new JsonArray();
        for (FourthRoomShopItem item : FourthRoomShopItem.values()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", item.id());
            itemJson.addProperty("displayName", shopItemDisplayName(item));
            itemJson.addProperty("description", shopItemDescription(item));
            itemJson.addProperty("price", config.getPrice(item.id()));
            itemJson.addProperty("ownedCount", ownedItemCount(state, item));
            itemJson.addProperty("canUse", shopItemCanUse(item));
            itemJson.addProperty("requiresTarget", shopItemRequiresTarget(item));
            shopItems.add(itemJson);
        }
        viewerJson.add("shopItems", shopItems);
        root.add("viewer", viewerJson);

        FourthRoomRoomState roomState = data.rooms.get(state.roomId);
        if (roomState != null) {
            root.addProperty("roomTurnNumber", roomState.turnNumber);
            if (roomState.activePlayerId != null) {
                root.addProperty("activePlayerId", roomState.activePlayerId.toString());
                root.addProperty("activePlayerName", playerName(roomState.activePlayerId));
            }
            for (FourthRoomPublicAction action : roomState.publicActions) {
                JsonObject actionJson = new JsonObject();
                actionJson.addProperty("sequence", action.sequence);
                actionJson.addProperty("tick", action.tick);
                actionJson.addProperty("category", action.category);
                actionJson.addProperty("actorName", action.actorName);
                actionJson.addProperty("verb", action.verb);
                actionJson.addProperty("subject", action.subject);
                actionJson.addProperty("targetName", action.targetName);
                actionJson.addProperty("detail", action.detail);
                roomActions.add(actionJson);
            }
            for (UUID occupantId : roomState.occupants) {
                FourthRoomPlayerState occupant = data.players.get(occupantId);
                if (occupant == null) {
                    continue;
                }
                JsonObject playerJson = new JsonObject();
                playerJson.addProperty("uuid", occupantId.toString());
                playerJson.addProperty("name", playerName(occupantId));
                playerJson.addProperty("alive", occupant.alive);
                playerJson.addProperty("self", occupantId.equals(state.playerId));
                playerJson.addProperty("currentTurn", occupantId.equals(roomState.activePlayerId));
                playerJson.addProperty("hiddenIdentityCount", occupant.hiddenIdentityCount());
                roomPlayers.add(playerJson);
            }
        }

        return root;
    }

    public ServerLevel level() {
        return level;
    }

    public FourthRoomSavedData data() {
        return data;
    }

    public FourthRoomTaskScheduler taskScheduler() {
        return taskScheduler;
    }

    public long currentTick() {
        return level.getGameTime();
    }

    private void syncRoomTables() {
        List<RoomDefinition> definitions = roomManager.buildRoomDefinitions();
        for (RoomDefinition definition : definitions) {
            FourthRoomTableBlockEntity table = getRoomTable(definition.roomId(), definitions);
            if (table == null) {
                continue;
            }
            FourthRoomRoomState roomState = data.rooms.get(definition.roomId());
            if (!data.active || roomState == null) {
                table.clearLinkedRoomState();
                continue;
            }
            table.applyRoomVisualState(
                    definition.roomId(),
                    buildSeatView(roomState, 0),
                    buildSeatView(roomState, 1),
                    roomDrawPileSize(roomState),
                    roomDiscardPileSize(roomState),
                    latestDiscardLabel(roomState),
                    roomState.activePlayerId == null ? "" : roomState.activePlayerId.toString(),
                    phaseDisplayName(data.phase),
                    "",
                    buildRecentTableCards(roomState));
        }
    }

    private void clearRoomTables() {
        List<RoomDefinition> definitions = roomManager.buildRoomDefinitions();
        for (RoomDefinition definition : definitions) {
            FourthRoomTableBlockEntity table = getRoomTable(definition.roomId(), definitions);
            if (table != null) {
                table.clearLinkedRoomState();
            }
        }
    }

    @Nullable
    private FourthRoomTableBlockEntity getRoomTable(int roomId, List<RoomDefinition> definitions) {
        if (roomId < 0 || roomId >= definitions.size()) {
            return null;
        }
        if (level.getBlockEntity(definitions.get(roomId).center()) instanceof FourthRoomTableBlockEntity table) {
            return table;
        }
        return null;
    }

    @Nullable
    private FourthRoomTableBlockEntity.SeatView buildSeatView(FourthRoomRoomState roomState, int seatIndex) {
        if (seatIndex < 0 || seatIndex >= roomState.occupants.size()) {
            return null;
        }
        UUID occupantId = roomState.occupants.get(seatIndex);
        FourthRoomPlayerState occupant = data.players.get(occupantId);
        if (occupant == null) {
            return null;
        }
        return new FourthRoomTableBlockEntity.SeatView(
                occupantId.toString(),
                playerName(occupantId),
                occupant.alive,
                occupant.hiddenIdentityCount(),
                buildPublicIdentitySlots(occupant));
    }

    private List<FourthRoomTableBlockEntity.TableCardDisplay> buildRecentTableCards(FourthRoomRoomState roomState) {
        List<FourthRoomTableBlockEntity.TableCardDisplay> displays = new ArrayList<>();
        int start = Math.max(0, roomState.publicActions.size() - 8);
        for (int index = start; index < roomState.publicActions.size(); index++) {
            FourthRoomPublicAction action = roomState.publicActions.get(index);
            if (!"card".equals(action.category) && !"skill".equals(action.category)) {
                continue;
            }
            if (action.subject == null || action.subject.isBlank()) {
                continue;
            }
            displays.add(new FourthRoomTableBlockEntity.TableCardDisplay(
                    actionTitle(action),
                    "",
                    categoryColor(action.category),
                    isHighlightCategory(action.category),
                    action.tick + action.sequence));
        }
        while (displays.size() > 6) {
            displays.removeFirst();
        }
        return displays;
    }

    private List<String> buildPublicIdentitySlots(FourthRoomPlayerState occupant) {
        List<String> identities = new ArrayList<>();
        for (int index = 0; index < occupant.identityBlocks.size(); index++) {
            boolean revealed = index < occupant.revealed.size() && occupant.revealed.get(index);
            identities.add(revealed ? occupant.identityBlocks.get(index) : "");
        }
        return identities;
    }

    private int roomDrawPileSize(FourthRoomRoomState roomState) {
        int size = 0;
        for (UUID occupantId : roomState.occupants) {
            FourthRoomPlayerState state = data.players.get(occupantId);
            if (state != null && state.alive) {
                size += state.drawPile.size();
            }
        }
        return size;
    }

    private int roomDiscardPileSize(FourthRoomRoomState roomState) {
        int size = 0;
        for (UUID occupantId : roomState.occupants) {
            FourthRoomPlayerState state = data.players.get(occupantId);
            if (state != null) {
                size += state.discardPile.size();
            }
        }
        return size;
    }

    private String latestDiscardLabel(FourthRoomRoomState roomState) {
        for (int index = roomState.publicActions.size() - 1; index >= 0; index--) {
            FourthRoomPublicAction action = roomState.publicActions.get(index);
            if (!action.subject.isBlank()) {
                return action.subject;
            }
        }
        return "";
    }

    private void emitDrawEffects(int roomId, UUID playerId, int amount) {
        if (roomId < 0 || amount <= 0) {
            return;
        }
        TableEffectEvents.TableAnchor anchor = anchorForPlayer(roomId, playerId);
        if (anchor == null) {
            return;
        }
        int color = 0xFF7CCBFF;
        List<EffectEvent> effects = new ArrayList<>();
        int motionCount = Math.min(3, Math.max(1, amount));
        for (int index = 0; index < motionCount; index++) {
            long offset = index * 120L;
            effects.add(new TableEffectEvents.CardMotion(offset, "摸牌", false,
                    TableEffectEvents.TableAnchor.DRAW_PILE, anchor, color, 320L));
        }
        effects.add(new TableEffectEvents.Pulse(60L, anchor, color, 0.75F, 360L));
        emitEffects(roomId, effects);
    }

    private void emitCardPlayEffects(int roomId, UUID actorId, String cardLabel, boolean gold, boolean skillCard) {
        if (roomId < 0) {
            return;
        }
        TableEffectEvents.TableAnchor anchor = anchorForPlayer(roomId, actorId);
        if (anchor == null) {
            return;
        }
        int color = gold ? 0xFFF2C56A : (skillCard ? 0xFF6FD6C5 : 0xFFF08B55);
        emitEffects(roomId, List.of(
                new TableEffectEvents.CardMotion(0L, cardLabel, gold, anchor,
                        TableEffectEvents.TableAnchor.CENTER, color, 360L),
                new TableEffectEvents.Pulse(150L, TableEffectEvents.TableAnchor.CENTER, color, 1.0F, 420L),
                new TableEffectEvents.CardMotion(320L, cardLabel, gold, TableEffectEvents.TableAnchor.CENTER,
                        TableEffectEvents.TableAnchor.DISCARD_PILE, color, 320L)));
    }

    private void emitActiveTurnFocus(int roomId, @Nullable UUID activePlayerId, long delayMs) {
        if (roomId < 0 || activePlayerId == null) {
            return;
        }
        TableEffectEvents.TableAnchor anchor = anchorForPlayer(roomId, activePlayerId);
        if (anchor == null) {
            return;
        }
        int color = 0xFF8BD5FF;
        emitEffects(roomId, List.of(
                new TableEffectEvents.CameraFocus(delayMs, anchor, 420L, 0.55F, false, 0),
                new TableEffectEvents.Pulse(delayMs + 60L, anchor, color, 0.90F, 420L),
                new TableEffectEvents.Banner(delayMs + 30L, "轮到 " + playerName(activePlayerId), color, 760L)));
    }

    private void emitActionLogEffects(int roomId, FourthRoomPublicAction action) {
        if (roomId < 0) {
            return;
        }
        int color = categoryColor(action.category);
        boolean heavy = isHeavyCategory(action.category);
        TableEffectEvents.TableAnchor actorAnchor = anchorForPlayerName(roomId, action.actorName);
        TableEffectEvents.TableAnchor targetAnchor = anchorForPlayerName(roomId, action.targetName);
        List<EffectEvent> effects = new ArrayList<>();
        if (actorAnchor != null && !action.actorName.isBlank() && !"系统".equals(action.actorName)) {
            effects.add(new TableEffectEvents.CameraFocus(0L, actorAnchor, 300L, 0.40F, false, 0));
        }
        if (targetAnchor != null && targetAnchor != actorAnchor) {
            effects.add(new TableEffectEvents.CameraFocus(150L, targetAnchor, heavy ? 460L : 340L,
                    heavy ? 0.72F : 0.52F, heavy, heavy ? color : 0));
        }
        TableEffectEvents.TableAnchor pulseAnchor = targetAnchor != null ? targetAnchor
                : actorAnchor != null ? actorAnchor : TableEffectEvents.TableAnchor.CENTER;
        effects.add(new TableEffectEvents.Pulse(90L, pulseAnchor, color, heavy ? 1.15F : 0.75F,
                heavy ? 520L : 360L));
        emitEffects(roomId, effects);
    }

    private void emitEffects(int roomId, List<EffectEvent> effects) {
        FourthRoomTableBlockEntity table = getRoomTable(roomId, roomManager.buildRoomDefinitions());
        if (table != null) {
            table.broadcastEffects(effects);
        }
    }

    @Nullable
    private TableEffectEvents.TableAnchor anchorForPlayer(int roomId, @Nullable UUID playerId) {
        if (playerId == null) {
            return null;
        }
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null) {
            return null;
        }
        int index = roomState.occupants.indexOf(playerId);
        return index >= 0 ? TableEffectEvents.TableAnchor.fromSeatIndex(index) : null;
    }

    @Nullable
    private TableEffectEvents.TableAnchor anchorForPlayerName(int roomId, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null) {
            return null;
        }
        for (int index = 0; index < roomState.occupants.size(); index++) {
            if (playerName(roomState.occupants.get(index)).equals(playerName)) {
                return TableEffectEvents.TableAnchor.fromSeatIndex(index);
            }
        }
        return null;
    }

    private boolean isHighlightCategory(String category) {
        return switch (category) {
            case "card", "skill", "damage", "reveal", "defense" -> true;
            default -> false;
        };
    }

    private boolean isHeavyCategory(String category) {
        return switch (category) {
            case "damage", "reveal", "defense" -> true;
            default -> false;
        };
    }

    private int categoryColor(String category) {
        return switch (category) {
            case "card" -> 0xFFF08B55;
            case "skill" -> 0xFF6FD6C5;
            case "damage" -> 0xFFE4574C;
            case "defense" -> 0xFF6CB5F5;
            case "reveal" -> 0xFFC883F1;
            case "system" -> 0xFFE3E3E3;
            default -> 0xFFD6CFBA;
        };
    }

    private String actionTitle(FourthRoomPublicAction action) {
        if (action == null) {
            return "系统";
        }
        if (!action.subject.isBlank()) {
            return action.subject;
        }
        return action.verb.isBlank() ? "系统" : action.verb;
    }

    private String formatActionSummary(FourthRoomPublicAction action) {
        if (action == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendToken(builder, action.actorName);
        appendToken(builder, action.verb);
        appendToken(builder, action.subject);
        appendToken(builder, action.targetName);
        appendToken(builder, action.detail);
        return builder.toString();
    }

    @SuppressWarnings("unused")
    private String formatActionBanner(FourthRoomPublicAction action) {
        String summary = formatActionSummary(action);
        return summary.isBlank() ? "牌桌状态已更新" : summary;
    }

    private void appendToken(StringBuilder builder, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(token);
    }

    private void assignTeamsAndIdentities(List<ServerPlayer> participants) {
        List<FourthRoomTeam> teams = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            teams.add(index < participants.size() / 2 ? FourthRoomTeam.RED : FourthRoomTeam.BLUE);
        }
        Collections.shuffle(teams);
        List<SkillCard> skills = new ArrayList<>(List.of(SkillCard.values()));
        Collections.shuffle(skills);
        for (int index = 0; index < participants.size(); index++) {
            ServerPlayer player = participants.get(index);
            FourthRoomPlayerState playerState = new FourthRoomPlayerState(player.getUUID());
            playerState.team = teams.get(index);
            playerState.identityBlocks.addAll(createIdentitySet(playerState.team));
            playerState.revealed.add(false);
            playerState.revealed.add(false);
            playerState.revealed.add(false);
            playerState.skillCardId = skills.get(index % skills.size()).id();
            data.players.put(playerState.playerId, playerState);
            resetCardsForRound(playerState);
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    private List<String> createIdentitySet(FourthRoomTeam team) {
        List<String> identities = new ArrayList<>();
        String teamBlock = team == FourthRoomTeam.RED ? config.redTeamBlock : config.blueTeamBlock;
        String oppositeBlock = team == FourthRoomTeam.RED ? config.blueTeamBlock : config.redTeamBlock;
        identities.add(teamBlock);
        identities.add(teamBlock);
        identities.add(oppositeBlock);
        Collections.shuffle(identities);
        return identities;
    }

    private void resetCardsForRound(FourthRoomPlayerState state) {
        state.drawPile.clear();
        state.hand.clear();
        state.discardPile.clear();
        state.peekCache.clear();
        state.lifeShield = 0;
        state.skipTurns = 0;
        state.extraTurns = 0;
        state.markedForKill = 0;
        state.decoyArmed = false;
        
        // 使用牌组预设创建固定牌组
        DeckPreset preset = DeckPreset.byId(config.deckPreset);
        List<CardInstance> deck = preset.createDeck();
        
        // 如果是混乱模式，额外打乱
        if (preset == DeckPreset.CHAOS) {
            Collections.shuffle(deck);
        }
        
        state.drawPile.addAll(deck);
        drawCards(state.playerId, 3, false);
        state.hand.add(new CardInstance(state.skillCardId, false));
    }

    private boolean isPlayersTurn(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return false;
        }
        if (roomManager.countLivingPlayers(state.roomId) <= 1) {
            return false;
        }
        FourthRoomRoomState roomState = data.rooms.get(state.roomId);
        return roomState != null && Objects.equals(roomState.activePlayerId, playerId);
    }

    public boolean skipCurrentTurn(UUID playerId) {
        FourthRoomPlayerState actorState = data.players.get(playerId);
        if (actorState == null) {
            return false;
        }
        FourthRoomRoomState roomState = data.rooms.get(actorState.roomId);
        if (roomState == null || roomState.activePlayerId == null) {
            return false;
        }
        UUID resolvedTarget = roomState.activePlayerId;
        FourthRoomPlayerState targetState = data.players.get(resolvedTarget);
        if (targetState == null || !targetState.alive) {
            return false;
        }
        if (!isPlayersTurn(resolvedTarget)) {
            return false;
        }
        
        // 立即跳过当前回合，不摸牌
        targetState.peekCache.clear();
        logPlayerRoomAction(playerId, "card", "施放了", cardDisplayName(BasicCard.SKIP), playerName(resolvedTarget),
                "立即跳过回合（不摸牌）");
        roomManager.advanceTurn(targetState.roomId);
        resolveTurnEntry(targetState.roomId);
        FourthRoomRoomState newRoomState = data.rooms.get(targetState.roomId);
        emitActiveTurnFocus(targetState.roomId, newRoomState != null ? newRoomState.activePlayerId : null, 140L);
        data.setDirty(true);
        syncMatchState();
        return true;
    }

    public boolean hasCardInHand(UUID playerId, String cardId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return false;
        }
        return state.hand.stream().anyMatch(card -> card.cardId().equals(cardId));
    }

    public boolean removeCardFromHand(UUID playerId, String cardId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return false;
        }
        CardInstance instance = state.hand.stream()
                .filter(card -> card.cardId().equals(cardId))
                .findFirst()
                .orElse(null);
        if (instance == null) {
            return false;
        }
        state.hand.remove(instance);
        return true;
    }

    private String phaseDisplayName(FourthRoomPhase phase) {
        return switch (phase) {
            case INACTIVE -> "未开始";
            case CARD_BATTLE -> "卡牌对战";
            case ROTATING -> "房间轮换";
            case DUEL -> "最终决斗";
            case FINISHED -> "已结束";
        };
    }

    private String teamDisplayName(FourthRoomTeam team) {
        return switch (team) {
            case RED -> "红队";
            case BLUE -> "蓝队";
        };
    }

    private String cardDisplayName(String cardId) {
        Card definition = CardRegistry.byId(cardId);
        return definition != null ? cardDisplayName(definition) : cardId;
    }

    private String cardDisplayName(Card card) {
        return switch (card.id()) {
            case "death" -> "死亡";
            case "cleanse" -> "净化";
            case "bottom_draw" -> "抽底";
            case "seize" -> "夺取";
            case "skip" -> "跳过";
            case "veto" -> "否决";
            case "point_kill" -> "点杀";
            case "dismantle" -> "拆解";
            case "peek" -> "窥视";
            case "life" -> "命格";
            case "copy" -> "复制";
            case "rebuild" -> "重构";
            case "interrogate" -> "审讯";
            case "decoy" -> "诱饵";
            case "first_aid" -> "急救";
            case "fate_shift" -> "命运转移";
            default -> card.id();
        };
    }

    private String cardDescription(Card card) {
        return switch (card.id()) {
            case "death" -> "抽到后立即对自己造成一次伤害。";
            case "cleanse" -> "将弃牌堆洗回牌库。";
            case "bottom_draw" -> "从牌库底部摸一张牌。";
            case "seize" -> "随机夺取目标一张可夺取卡牌。";
            case "skip" -> "立即跳过当前玩家的回合，不摸牌。";
            case "veto" -> "任何时候可以打出，强制目标摸一张牌。可以对自己使用，也可以被否决抵消。";
            case "point_kill" -> "给目标额外一个回合。";
            case "dismantle" -> "随机将目标一张卡塞回其牌库。";
            case "peek" -> "查看自己牌库顶的三张牌。";
            case "life" -> "被动卡牌：抽到死亡牌时自动使用并抵挡伤害，无法主动打出。";
            case "copy" -> "复制对手手牌中1张非技能牌，加入自己手牌。";
            case "rebuild" -> "洗回弃牌堆并再摸两张牌。";
            case "interrogate" -> "强制目标展示并埋回一张可夺取牌。";
            case "decoy" -> "下次受到伤害时转嫁给同房对手。";
            case "first_aid" -> "恢复一块已翻开的身份块。";
            case "fate_shift" -> "减少一点点杀并窥视两张牌。";
            default -> card.id();
        };
    }

    private boolean cardRequiresTarget(Card card, boolean gold) {
        return switch (card.id()) {
            case "point_kill" -> gold;
            case "seize", "veto", "dismantle", "interrogate", "copy" -> true;
            case "skip" -> false;
            default -> false;
        };
    }

    public String shopItemDisplayName(FourthRoomShopItem item) {
        return Component.translatable("shop.fourth_room." + item.id()).getString();
    }

    private String shopItemDescription(FourthRoomShopItem item) {
        return Component.translatable("shop.fourth_room." + item.id() + ".description").getString();
    }

    private boolean shopItemCanUse(FourthRoomShopItem item) {
        return switch (item) {
            case SCORPION, HANDGUN, POISON_MUSHROOM -> true;
            default -> false;
        };
    }

    private boolean shopItemRequiresTarget(FourthRoomShopItem item) {
        return switch (item) {
            case SCORPION, HANDGUN, POISON_MUSHROOM -> true;
            default -> false;
        };
    }

    private int ownedItemCount(FourthRoomPlayerState state, FourthRoomShopItem item) {
        return switch (item) {
            case SCORPION -> state.scorpionCharges;
            case HANDGUN -> state.handgunCharges;
            case POISON_MUSHROOM -> state.poisonMushroomCharges;
            case BULLETPROOF_VEST -> state.bulletproofVestCharges;
            case TEST_STRIP -> state.testStripCharges;
            case STICKY_NOTE -> state.stickyNoteCharges;
        };
    }

    public String playerName(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        return player != null ? player.getScoreboardName() : playerId.toString().substring(0, 8);
    }

    public void logPlayerRoomAction(UUID playerId, String category, String verb, String subject, String targetName,
            String detail) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return;
        }
        logRoomAction(state.roomId, category, playerName(playerId), verb, subject, targetName, detail);
    }

    public void logRoomAction(int roomId, String category, String actorName, String verb, String subject,
            String targetName, String detail) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null) {
            return;
        }
        FourthRoomPublicAction action = new FourthRoomPublicAction();
        action.sequence = roomState.nextPublicActionSequence++;
        action.tick = currentTick();
        action.category = category == null || category.isBlank() ? "system" : category;
        action.actorName = actorName == null ? "" : actorName;
        action.verb = verb == null ? "" : verb;
        action.subject = subject == null ? "" : subject;
        action.targetName = targetName == null ? "" : targetName;
        action.detail = detail == null ? "" : detail;
        roomState.publicActions.add(action);
        while (roomState.publicActions.size() > 12) {
            roomState.publicActions.removeFirst();
        }
        data.setDirty(true);
        emitActionLogEffects(roomId, action);
    }

    private String shortBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "未知方块";
        }
        int separator = blockId.indexOf(':');
        return separator >= 0 ? blockId.substring(separator + 1) : blockId;
    }

    private String reasonDisplay(String reason) {
        return switch (reason) {
            case "scorpion" -> "蝎子";
            case "handgun" -> "手枪";
            case "poison_mushroom" -> "毒蘑菇";
            case "point_kill" -> "点杀";
            case "death_card" -> "死亡卡";
            default -> reason == null ? "未知原因" : reason;
        };
    }

    private void broadcastReveal(UUID playerId, String blockId) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        String name = player != null ? player.getScoreboardName() : playerId.toString();
        broadcast(name + " revealed identity block " + blockId);
    }

    private void startRotation() {
        data.phase = FourthRoomPhase.ROTATING;
        data.rotationCount++;
        data.rotationResumeTick = currentTick() + config.lobbyWaitSeconds * 20L;
        taskScheduler.clearActiveTask();
        for (FourthRoomPlayerState playerState : data.players.values()) {
            if (!playerState.alive) {
                continue;
            }
            restoreOneIdentity(playerState.playerId);
            resetCardsForRound(playerState);
        }
        roomManager.teleportAlivePlayersToLobby();
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            logRoomAction(roomState.roomId, "system", "系统", "开始房间轮换", "", "", "");
        }
        data.setDirty(true);
        broadcast("Rotation " + data.rotationCount + " started.");
        syncMatchState();
    }

    private void finishRotation() {
        roomManager.assignRooms(new ArrayList<>(data.players.values()));
        roomManager.teleportPlayersToRooms();
        data.phase = FourthRoomPhase.CARD_BATTLE;
        data.nextRotationTick = currentTick() + config.rotationIntervalSeconds * 20L;
        taskScheduler.scheduleNextTask(currentTick());
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            logRoomAction(roomState.roomId, "system", "系统", "新一轮对局开始", "", "", "");
        }
        data.setDirty(true);
        syncMatchState();
        for (FourthRoomRoomState roomState : data.rooms.values()) {
            emitActiveTurnFocus(roomState.roomId, roomState.activePlayerId, 80L);
        }
    }

    private void resolveTurnEntry(int roomId) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null || roomState.activePlayerId == null) {
            return;
        }
        if (roomManager.countLivingPlayers(roomId) <= 1) {
            roomManager.refreshRoomTurnState(roomId);
            return;
        }
        for (int guard = 0; guard < Math.max(1, roomState.occupants.size()); guard++) {
            FourthRoomPlayerState current = data.players.get(roomState.activePlayerId);
            if (current == null || !current.alive) {
                roomManager.advanceTurn(roomId);
                roomState = data.rooms.get(roomId);
                if (roomState == null || roomState.activePlayerId == null) {
                    return;
                }
                continue;
            }
            if (current.markedForKill > 0) {
                int stacks = current.markedForKill;
                current.markedForKill = 0;
                for (int hit = 0; hit < stacks && current.alive; hit++) {
                    inflictCardDamage(current.playerId, current.playerId, "point_kill");
                }
                if (!current.alive) {
                    roomManager.advanceTurn(roomId);
                    roomState = data.rooms.get(roomId);
                    if (roomState == null || roomState.activePlayerId == null) {
                        return;
                    }
                    continue;
                }
            }
            if (current.skipTurns > 0) {
                current.skipTurns--;
                logRoomAction(roomId, "system", playerName(current.playerId), "的回合被跳过", "跳过", "", "");
                roomManager.advanceTurn(roomId);
                roomState = data.rooms.get(roomId);
                if (roomState == null || roomState.activePlayerId == null) {
                    return;
                }
                continue;
            }
            return;
        }
    }

    private String formatDrawnCardName(CardInstance instance) {
        String name = cardDisplayName(instance.cardId());
        return instance.gold() ? name + "[金]" : name;
    }

    private void processPoisonDeaths() {
        for (FourthRoomPlayerState playerState : new ArrayList<>(data.players.values())) {
            if (playerState.alive && playerState.pendingPoisonDeathTick > 0L
                    && currentTick() >= playerState.pendingPoisonDeathTick) {
                playerState.pendingPoisonDeathTick = -1L;
                eliminatePlayer(playerState.playerId, "poison_mushroom");
            }
        }
    }
}