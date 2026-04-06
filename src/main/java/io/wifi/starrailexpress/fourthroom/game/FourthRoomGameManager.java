package io.wifi.starrailexpress.fourthroom.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.fourthroom.card.BasicCard;
import io.wifi.starrailexpress.fourthroom.card.Card;
import io.wifi.starrailexpress.fourthroom.card.CardInstance;
import io.wifi.starrailexpress.fourthroom.card.CardRegistry;
import io.wifi.starrailexpress.fourthroom.card.SkillCard;
import io.wifi.starrailexpress.fourthroom.config.FourthRoomConfig;
import io.wifi.starrailexpress.fourthroom.duel.FourthRoomDuelManager;
import io.wifi.starrailexpress.fourthroom.network.FourthRoomStatePayload;
import io.wifi.starrailexpress.fourthroom.room.RoomManager;
import io.wifi.starrailexpress.fourthroom.shop.FourthRoomShopItem;
import io.wifi.starrailexpress.fourthroom.shop.FourthRoomShopService;
import io.wifi.starrailexpress.fourthroom.task.FourthRoomTaskType;
import io.wifi.starrailexpress.fourthroom.task.FourthRoomTaskScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        int requested = Math.max(2, data.requestedPlayerCount > 0 ? data.requestedPlayerCount : config.defaultPlayerCount);
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
        CardInstance instance = playerState.hand.stream()
                .filter(card -> card.cardId().equals(cardId))
                .findFirst()
                .orElse(null);
        if (instance == null) {
            return false;
        }
        Card card = CardRegistry.byId(instance.cardId());
        if (card == null) {
            return false;
        }
        if (!card.isSkill() && !isPlayersTurn(playerId)) {
            return false;
        }
        UUID resolvedTarget = targetId != null ? targetId : roomManager.getOpponent(playerId);
        if (cardId.equals("point_kill") && !instance.gold()) {
            resolvedTarget = playerId;
        }
        playerState.hand.remove(instance);
        boolean success = card.play(this, playerId, resolvedTarget, instance);
        if (!success) {
            playerState.hand.add(instance);
            return false;
        }
        playerState.discardPile.add(instance);
        logPlayerRoomAction(playerId,
                card.isSkill() ? "skill" : "card",
                "使用了",
                cardDisplayName(card),
                playerName(resolvedTarget),
                instance.gold() ? "金卡" : "");
        data.setDirty(true);
        syncMatchState();
        return true;
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
        }
        data.setDirty(true);
    }

    public void shuffleDiscardIntoDeck(UUID playerId) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null || state.discardPile.isEmpty()) {
            return;
        }
        state.drawPile.addAll(state.discardPile);
        state.discardPile.clear();
        Collections.shuffle(state.drawPile);
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
    }

    public void inflictCardDamage(UUID sourceId, UUID targetId, String reason) {
        FourthRoomPlayerState targetState = data.players.get(targetId);
        if (targetState == null || !targetState.alive) {
            return;
        }
        if (targetState.lifeShield > 0) {
            targetState.lifeShield--;
            sendPrivate(targetId, "A life card blocked incoming damage.");
            logRoomAction(targetState.roomId, "defense", playerName(targetId), "抵挡了伤害", "命格", "", "");
            data.setDirty(true);
            return;
        }
        if (targetState.decoyArmed) {
            targetState.decoyArmed = false;
            UUID opponent = roomManager.getOpponent(targetId);
            if (opponent != null) {
                logRoomAction(targetState.roomId, "defense", playerName(targetId), "发动了", "诱饵", playerName(opponent), "转移伤害");
                inflictCardDamage(sourceId, opponent, "decoy:" + reason);
                data.setDirty(true);
                return;
            }
        }
        int hiddenIdentityIndex = targetState.firstHiddenIdentityIndex();
        if (hiddenIdentityIndex >= 0) {
            targetState.revealed.set(hiddenIdentityIndex, true);
            logRoomAction(targetState.roomId, "damage", playerName(targetId), "被打翻了一块身份", shortBlockId(targetState.identityBlocks.get(hiddenIdentityIndex)), "", "");
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
            player.displayClientMessage(Component.literal("You were eliminated by " + reason).withStyle(ChatFormatting.RED), false);
        }
        data.setDirty(true);
        duelManager.maybeResolveWinCondition();
    }

    public boolean buyItem(UUID playerId, FourthRoomShopItem item) {
        boolean success = shopService.buy(playerId, item);
        if (success) {
            sendPrivate(playerId, "Purchased " + item.id());
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

    public boolean placeStickyNote(UUID playerId, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face, String text) {
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
            sendPrivate(playerId, "+" + amount + " gold (" + reason + ")");
        }
    }

    public void broadcast(String message) {
        Component component = Component.literal("[Fourth Room] " + message).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(component, false);
        }
    }

    public void sendPrivate(UUID playerId, String message) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.displayClientMessage(Component.literal("[Fourth Room] " + message).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public void syncMatchState() {
        data.setDirty(true);
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
        root.addProperty("activeTaskDescription", activeTask != null ? activeTask.description() : "");
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
            cardJson.addProperty("description", definition != null ? cardDescription(definition) : cardInstance.cardId());
            cardJson.addProperty("requiresTarget", definition != null && cardRequiresTarget(definition, cardInstance.gold()));
            cardJson.addProperty("skill", definition != null && definition.isSkill());
            hand.add(cardJson);
        }
        viewerJson.add("hand", hand);

        JsonArray peek = new JsonArray();
        for (String cardId : state.peekCache) {
            JsonObject peekJson = new JsonObject();
            peekJson.addProperty("id", cardId);
            peekJson.addProperty("displayName", cardDisplayName(cardId));
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
        List<CardInstance> deck = new ArrayList<>();
        deck.add(new CardInstance(BasicCard.DEATH.id(), false));
        deck.add(new CardInstance(BasicCard.DEATH.id(), false));
        deck.add(new CardInstance(BasicCard.DEATH.id(), false));
        deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
        deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
        deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
        deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
        deck.add(new CardInstance(BasicCard.SKIP.id(), false));
        deck.add(new CardInstance(BasicCard.VETO.id(), false));
        deck.add(new CardInstance(BasicCard.POINT_KILL.id(), false));
        deck.add(new CardInstance(BasicCard.POINT_KILL.id(), true));
        deck.add(new CardInstance(BasicCard.DISMANTLE.id(), false));
        deck.add(new CardInstance(BasicCard.PEEK.id(), false));
        deck.add(new CardInstance(BasicCard.LIFE.id(), false));
        deck.add(new CardInstance(BasicCard.LIFE.id(), false));
        Collections.shuffle(deck);
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
        endTurn(resolvedTarget);
        logPlayerRoomAction(playerId, "card", "施放了", cardDisplayName(BasicCard.SKIP), playerName(resolvedTarget), "立即跳过当前玩家回合");
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
            case "skip" -> "立即跳过当前玩家的回合。";
            case "veto" -> "任何时候可以打出，强制目标摸一张牌。可以对自己使用，也可以被否决抵消。";
            case "point_kill" -> "给目标额外一个回合。";
            case "dismantle" -> "随机将目标一张卡塞回其牌库。";
            case "peek" -> "查看自己牌库顶的三张牌。";
            case "life" -> "获得一层伤害护盾。";
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
            case "seize", "veto", "dismantle", "interrogate" -> true;
            case "skip" -> false;
            default -> false;
        };
    }

    public String shopItemDisplayName(FourthRoomShopItem item) {
        return switch (item) {
            case SCORPION -> "蝎子";
            case HANDGUN -> "手枪";
            case POISON_MUSHROOM -> "毒蘑菇";
            case BULLETPROOF_VEST -> "防弹衣";
            case TEST_STRIP -> "试纸";
            case STICKY_NOTE -> "便利贴";
        };
    }

    private String shopItemDescription(FourthRoomShopItem item) {
        return switch (item) {
            case SCORPION -> "任务期间可直接处决一个目标。";
            case HANDGUN -> "任务期间射击目标，防弹衣可挡。";
            case POISON_MUSHROOM -> "任务期间给目标挂上延时死亡。";
            case BULLETPROOF_VEST -> "被手枪命中时自动抵挡一次。";
            case TEST_STRIP -> "保留作后续任务与检定扩展。";
            case STICKY_NOTE -> "保留作便利贴放置与搜索扩展。";
        };
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

    public void logPlayerRoomAction(UUID playerId, String category, String verb, String subject, String targetName, String detail) {
        FourthRoomPlayerState state = data.players.get(playerId);
        if (state == null) {
            return;
        }
        logRoomAction(state.roomId, category, playerName(playerId), verb, subject, targetName, detail);
    }

    public void logRoomAction(int roomId, String category, String actorName, String verb, String subject, String targetName, String detail) {
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
            if (playerState.alive && playerState.pendingPoisonDeathTick > 0L && currentTick() >= playerState.pendingPoisonDeathTick) {
                playerState.pendingPoisonDeathTick = -1L;
                eliminatePlayer(playerState.playerId, "poison_mushroom");
            }
        }
    }
}