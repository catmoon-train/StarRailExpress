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
        data.resetMatchState();
        syncMatchState();
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
        playerState.hand.remove(instance);
        boolean success = card.play(this, playerId, resolvedTarget, instance);
        if (!success) {
            playerState.hand.add(instance);
            return false;
        }
        playerState.discardPile.add(instance);
        data.setDirty(true);
        syncMatchState();
        return true;
    }

    public boolean endTurn(UUID playerId) {
        FourthRoomPlayerState playerState = data.players.get(playerId);
        if (playerState == null || !isPlayersTurn(playerId)) {
            return false;
        }
        drawCards(playerId, 1, false);
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
        for (int drawn = 0; drawn < amount; drawn++) {
            if (state.drawPile.isEmpty()) {
                shuffleDiscardIntoDeck(playerId);
            }
            if (state.drawPile.isEmpty()) {
                return;
            }
            CardInstance instance = fromBottom ? state.drawPile.removeFirst() : state.drawPile.removeLast();
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
    }

    public void inflictCardDamage(UUID sourceId, UUID targetId, String reason) {
        FourthRoomPlayerState targetState = data.players.get(targetId);
        if (targetState == null || !targetState.alive) {
            return;
        }
        if (targetState.lifeShield > 0) {
            targetState.lifeShield--;
            sendPrivate(targetId, "A life card blocked incoming damage.");
            data.setDirty(true);
            return;
        }
        if (targetState.decoyArmed) {
            targetState.decoyArmed = false;
            UUID opponent = roomManager.getOpponent(targetId);
            if (opponent != null) {
                inflictCardDamage(sourceId, opponent, "decoy:" + reason);
                data.setDirty(true);
                return;
            }
        }
        int hiddenIdentityIndex = targetState.firstHiddenIdentityIndex();
        if (hiddenIdentityIndex >= 0) {
            targetState.revealed.set(hiddenIdentityIndex, true);
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
        root.addProperty("phase", data.phase.name());
        root.addProperty("rotationCount", data.rotationCount);
        root.addProperty("nextRotationTick", data.nextRotationTick);
        root.addProperty("activeTaskId", data.activeTaskId);
        root.addProperty("taskDeadlineTick", data.taskDeadlineTick);
        if (data.winner != null) {
            root.addProperty("winner", data.winner.name());
        }
        JsonArray playersJson = new JsonArray();
        for (FourthRoomPlayerState state : data.players.values()) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("uuid", state.playerId.toString());
            playerJson.addProperty("alive", state.alive);
            playerJson.addProperty("team", state.team.name());
            playerJson.addProperty("roomId", state.roomId);
            playerJson.addProperty("coins", state.coins);
            playerJson.addProperty("hiddenIdentityCount", state.hiddenIdentityCount());
            if (viewer.getUUID().equals(state.playerId)) {
                JsonArray identities = new JsonArray();
                for (int index = 0; index < state.identityBlocks.size(); index++) {
                    JsonObject identity = new JsonObject();
                    identity.addProperty("blockId", state.identityBlocks.get(index));
                    identity.addProperty("revealed", state.revealed.get(index));
                    identities.add(identity);
                }
                JsonArray hand = new JsonArray();
                for (CardInstance cardInstance : state.hand) {
                    JsonObject cardJson = new JsonObject();
                    cardJson.addProperty("id", cardInstance.cardId());
                    cardJson.addProperty("gold", cardInstance.gold());
                    hand.add(cardJson);
                }
                playerJson.add("identities", identities);
                playerJson.add("hand", hand);
                JsonArray peek = new JsonArray();
                for (String cardId : state.peekCache) {
                    peek.add(cardId);
                }
                playerJson.add("peekCache", peek);
            }
            playersJson.add(playerJson);
        }
        root.add("players", playersJson);
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
        state.markedForKill = 0;
        state.decoyArmed = false;
        List<CardInstance> deck = new ArrayList<>();
        deck.add(new CardInstance(BasicCard.DEATH.id(), false));
        deck.add(new CardInstance(BasicCard.CLEANSE.id(), false));
        deck.add(new CardInstance(BasicCard.BOTTOM_DRAW.id(), true));
        deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
        deck.add(new CardInstance(BasicCard.SEIZE.id(), false));
        deck.add(new CardInstance(BasicCard.SKIP.id(), false));
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
        FourthRoomRoomState roomState = data.rooms.get(state.roomId);
        return roomState != null && Objects.equals(roomState.activePlayerId, playerId);
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
        data.setDirty(true);
        syncMatchState();
    }

    private void resolveTurnEntry(int roomId) {
        FourthRoomRoomState roomState = data.rooms.get(roomId);
        if (roomState == null || roomState.activePlayerId == null) {
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

    private void processPoisonDeaths() {
        for (FourthRoomPlayerState playerState : new ArrayList<>(data.players.values())) {
            if (playerState.alive && playerState.pendingPoisonDeathTick > 0L && currentTick() >= playerState.pendingPoisonDeathTick) {
                playerState.pendingPoisonDeathTick = -1L;
                eliminatePlayer(playerState.playerId, "poison_mushroom");
            }
        }
    }
}