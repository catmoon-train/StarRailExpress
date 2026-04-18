package org.agmas.noellesroles.game.modes.fourthroom.block;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.game.modes.fourthroom.card.Card;
import org.agmas.noellesroles.game.modes.fourthroom.card.CardInstance;
import org.agmas.noellesroles.game.modes.fourthroom.card.CardRegistry;
import org.agmas.noellesroles.game.modes.fourthroom.effect.EffectEvent;
import org.agmas.noellesroles.game.modes.fourthroom.effect.EffectQueue;
import org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomPlayerState;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomSavedData;
import org.agmas.noellesroles.game.modes.fourthroom.network.FourthRoomTableEffectsPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * In-world host for a FourthRoom card battle table.
 * Holds the visual state synced to clients and drives animations via EffectQueue.
 * The authoritative game logic still lives in FourthRoomGameManager / FourthRoomSavedData;
 * this entity acts as a rendering + interaction bridge.
 */
public class FourthRoomTableBlockEntity extends BlockEntity {

    private static final int MAX_SEATED_PLAYERS = 2;

    /** Room ID this table is linked to (-1 = unlinked). */
    private int linkedRoomId = -1;

    /** UUIDs of players currently seated at this table. */
    private final List<UUID> seatedPlayers = new ArrayList<>();

    /** Last action description shown on the table surface. */
    private String lastActionText = "";

    /** Seat A player uuid/name snapshot. */
    private String seatAPlayerUuid = "";
    private String seatAPlayerName = "";
    private boolean seatAAlive;
    private int seatAHiddenIdentityCount;
    private final List<String> seatAIdentitySlots = new ArrayList<>();

    /** Seat B player uuid/name snapshot. */
    private String seatBPlayerUuid = "";
    private String seatBPlayerName = "";
    private boolean seatBAlive;
    private int seatBHiddenIdentityCount;
    private final List<String> seatBIdentitySlots = new ArrayList<>();

    /** Number of cards in draw pile (for visual stack height). */
    private int drawPileSize;

    /** Number of cards in discard pile. */
    private int discardPileSize;

    /** Top discard card id for face-up rendering. */
    private String topDiscardCardId = "";

    /** Active player UUID (for turn indicator). */
    private String activePlayerUuid = "";

    /** Current game phase name. */
    private String phase = "";

    // --- Client-side animation ---

    /** Client-side effect queue for timed animations. */
    private transient EffectQueue clientEffectQueue;

    /** Client-side animation tick counter. */
    private transient int clientTicks;

    /** Table actions displayed as physical cards on the surface. */
    private final List<TableCardDisplay> tableCards = new ArrayList<>();

    /** Temporary in-world flying cards and highlight rings. */
    private transient List<ActiveCardAnimation> activeCardAnimations = new ArrayList<>();
    private transient List<AnchorPulseState> activePulses = new ArrayList<>();
    private transient BannerState activeBanner;

    public FourthRoomTableBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.FOURTH_ROOM_TABLE, pos, state);
    }

    // ── Server Methods ──────────────────────────────────────────

    public void onPlayerInteract(ServerPlayer player) {
        if (level == null || level.isClientSide) return;

        UUID uuid = player.getUUID();
        boolean joined = false;
        if (seatedPlayers.contains(uuid)) {
            seatedPlayers.remove(uuid);
            player.displayClientMessage(Component.literal("§e[第四房间] §f已离开牌桌"), true);
        } else if (seatedPlayers.size() < MAX_SEATED_PLAYERS) {
            seatedPlayers.add(uuid);
            joined = true;
            player.displayClientMessage(Component.literal("§e[第四房间] §f已加入牌桌 (" + seatedPlayers.size() + "/" + MAX_SEATED_PLAYERS + ")"), true);
        } else {
            player.displayClientMessage(Component.literal("§e[第四房间] §c当前牌桌只支持双人对战"), true);
        }
        if (joined) {
            tryStartTableMatch();
        }
        sync();
    }

    public boolean handleBattleInteraction(ServerPlayer player, FourthRoomTableBlock.InteractionZone zone) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        FourthRoomGameManager manager = FourthRoomGameManager.of(serverLevel);
        FourthRoomPlayerState playerState = manager.data().players.get(player.getUUID());
        if (linkedRoomId < 0 || !manager.data().active || playerState == null || playerState.roomId != linkedRoomId) {
            player.displayClientMessage(Component.literal("§e[第四房间] §c请操作自己房间内的牌桌"), true);
            return false;
        }
        if (!playerState.alive) {
            player.displayClientMessage(Component.literal("§e[第四房间] §c你已经出局，无法继续操作牌桌"), true);
            return false;
        }

        UUID playerId = player.getUUID();
        if (player.isShiftKeyDown() && zone.isSeat()) {
            UUID seatPlayerId = playerUuidForZone(zone);
            if (!playerId.equals(seatPlayerId)) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c只能潜行翻开自己的身份牌"), true);
                return false;
            }
            if (!manager.canRevealOwnIdentity(playerId) || !manager.revealOwnIdentity(playerId)) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c当前没有可翻开的身份牌"), true);
                return false;
            }
            return true;
        }

        if (zone == FourthRoomTableBlock.InteractionZone.DRAW_PILE) {
            if (!manager.canEndTurn(playerId)) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c只能在自己的回合右键牌库结束回合"), true);
                return false;
            }
            if (!manager.endTurn(playerId)) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c当前无法结束回合"), true);
                return false;
            }
            return true;
        }

        if (playerState.hand.isEmpty()) {
            player.displayClientMessage(Component.literal("§e[第四房间] §c你现在没有可打出的手牌"), true);
            return false;
        }

        int handIndex = player.getInventory().selected;
        if (handIndex < 0 || handIndex >= playerState.hand.size()) {
            player.displayClientMessage(Component.literal("§e[第四房间] §c当前物品栏槽位没有对应手牌，滚轮切到有牌的位置"), true);
            return false;
        }

        CardInstance instance = playerState.hand.get(handIndex);
        Card card = CardRegistry.byId(instance.cardId());
        if (card == null) {
            player.displayClientMessage(Component.literal("§e[第四房间] §c这张牌当前无法解析"), true);
            return false;
        }

        if (manager.cardRequiresTarget(instance)) {
            if (!zone.isSeat()) {
                player.displayClientMessage(Component.literal("§e[第四房间] §f这张牌需要右键目标身份牌来使用"), true);
                return false;
            }
            UUID targetId = playerUuidForZone(zone);
            List<UUID> validTargets = manager.validCardTargets(playerId, instance);
            if (targetId == null) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c该位置当前没有可指定的目标"), true);
                return false;
            }
            if (!validTargets.contains(targetId)) {
                player.displayClientMessage(Component.literal(playerId.equals(targetId)
                        ? "§e[第四房间] §c这张牌不能对自己使用"
                        : "§e[第四房间] §c目标当前不可被这张牌指定"), true);
                return false;
            }
            if (!manager.playCardByHandIndex(playerId, handIndex, targetId)) {
                player.displayClientMessage(Component.literal("§e[第四房间] §c当前无法打出这张牌"), true);
                return false;
            }
            return true;
        }

        if (!manager.playCardByHandIndex(playerId, handIndex, null)) {
            player.displayClientMessage(Component.literal(card.isSkill()
                    ? "§e[第四房间] §c当前无法施放这张技能牌"
                    : "§e[第四房间] §c还没轮到你出牌"), true);
            return false;
        }
        return true;
    }

    private void tryStartTableMatch() {
        if (!(level instanceof ServerLevel serverLevel) || seatedPlayers.size() < MAX_SEATED_PLAYERS) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        if (gameWorld.getGameStatus() != SREGameWorldComponent.GameStatus.INACTIVE) {
            notifySeatedPlayers(serverLevel, Component.literal("§e[第四房间] §c当前已有其他对局正在运行"));
            return;
        }
        if (!isSceneTable(serverLevel)) {
            notifySeatedPlayers(serverLevel, Component.literal("§e[第四房间] §c这张牌桌未绑定四号房场景，先生成测试场景"));
            return;
        }

        List<ServerPlayer> participants = seatedPlayers.stream()
                .map(serverLevel.getServer().getPlayerList()::getPlayer)
                .filter(Objects::nonNull)
                .toList();
        if (participants.size() < MAX_SEATED_PLAYERS) {
            notifySeatedPlayers(serverLevel, Component.literal("§e[第四房间] §c有玩家不在线，无法开始双人对局"));
            return;
        }

        BlockPos lobbyPos = FourthRoomSavedData.get(serverLevel).sceneLayout.lobbyPos;
        for (int index = 0; index < participants.size(); index++) {
            ServerPlayer participant = participants.get(index);
            double xOffset = index == 0 ? -0.85D : 0.85D;
            participant.teleportTo(serverLevel, lobbyPos.getX() + 0.5D + xOffset, lobbyPos.getY(), lobbyPos.getZ() + 0.5D,
                    participant.getYRot(), participant.getXRot());
        }

        FourthRoomGameManager.setRequestedPlayerCount(serverLevel, MAX_SEATED_PLAYERS);
        GameUtils.setForcedReadyPlayers(seatedPlayers);
        GameUtils.startGame(serverLevel, SREGameModes.FOURTH_ROOM,
                GameConstants.getInTicks(SREGameModes.FOURTH_ROOM.defaultStartTime, 0));
        notifySeatedPlayers(serverLevel, Component.literal("§e[第四房间] §f2/2 已就绪，正在开启双人对战"));
    }

    private boolean isSceneTable(ServerLevel serverLevel) {
        FourthRoomSavedData data = FourthRoomSavedData.get(serverLevel);
        return data.sceneLayout.hasRooms() && data.sceneLayout.rooms.stream()
                .anyMatch(room -> room.center().equals(getBlockPos()));
    }

    private void notifySeatedPlayers(ServerLevel serverLevel, Component message) {
        for (UUID seatedPlayer : seatedPlayers) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(seatedPlayer);
            if (player != null) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /**
     * Called by FourthRoomGameManager to push room visual state to the world table.
     */
    public void applyRoomVisualState(int roomId, @Nullable SeatView seatA, @Nullable SeatView seatB,
                                     int drawPile, int discardPile, String topDiscard,
                                     String activePlayer, String phase, String actionText,
                                     List<TableCardDisplay> recentCards) {
        this.linkedRoomId = roomId;
        this.drawPileSize = drawPile;
        this.discardPileSize = discardPile;
        this.topDiscardCardId = topDiscard != null ? topDiscard : "";
        this.activePlayerUuid = activePlayer != null ? activePlayer : "";
        this.phase = phase != null ? phase : "";
        this.lastActionText = actionText != null ? actionText : "";
        applySeat(true, seatA);
        applySeat(false, seatB);
        this.tableCards.clear();
        this.tableCards.addAll(recentCards);
        sync();
    }

    public void clearLinkedRoomState() {
        linkedRoomId = -1;
        drawPileSize = 0;
        discardPileSize = 0;
        topDiscardCardId = "";
        activePlayerUuid = "";
        phase = "";
        lastActionText = "";
        seatAPlayerUuid = "";
        seatAPlayerName = "";
        seatAAlive = false;
        seatAHiddenIdentityCount = 0;
        seatAIdentitySlots.clear();
        seatBPlayerUuid = "";
        seatBPlayerName = "";
        seatBAlive = false;
        seatBHiddenIdentityCount = 0;
        seatBIdentitySlots.clear();
        tableCards.clear();
        if (clientEffectQueue != null) {
            clientEffectQueue.clear();
        }
        activeCardAnimations.clear();
        activePulses.clear();
        activeBanner = null;
        sync();
    }

    /**
     * Push a list of effects to nearby clients.
     */
    public void broadcastEffects(List<EffectEvent> effects) {
        if (!(level instanceof ServerLevel serverLevel) || effects.isEmpty()) return;
        FourthRoomTableEffectsPayload.sendNearby(serverLevel, getBlockPos(), effects);
    }

    public void addTableCard(String titleText, String summaryText, int accentColor, boolean highlight) {
        tableCards.add(new TableCardDisplay(titleText, summaryText, accentColor, highlight, System.currentTimeMillis()));
        if (tableCards.size() > 8) {
            tableCards.removeFirst();
        }
        sync();
    }

    public int linkedRoomId() {
        return linkedRoomId;
    }

    public void setLinkedRoomId(int roomId) {
        this.linkedRoomId = roomId;
        sync();
    }

    public List<UUID> seatedPlayers() {
        return seatedPlayers;
    }

    // ── Client Methods ──────────────────────────────────────────

    public void clientTick() {
        clientTicks++;
        if (clientEffectQueue == null) {
            clientEffectQueue = new EffectQueue();
            clientEffectQueue.setOrigin(getBlockPos());
        }
        clientEffectQueue.tick();
        long now = System.currentTimeMillis();
        activeCardAnimations.removeIf(animation -> animation.expired(now));
        activePulses.removeIf(pulse -> pulse.expired(now));
        if (activeBanner != null && activeBanner.expired(now)) {
            activeBanner = null;
        }
    }

    public int clientTicks() {
        return clientTicks;
    }

    public EffectQueue clientEffectQueue() {
        if (clientEffectQueue == null) {
            clientEffectQueue = new EffectQueue();
            clientEffectQueue.setOrigin(getBlockPos());
        }
        return clientEffectQueue;
    }

    public void startCardAnimation(String label, boolean gold, TableEffectEvents.TableAnchor from,
                                   TableEffectEvents.TableAnchor to, int color, long durationMs) {
        activeCardAnimations.add(new ActiveCardAnimation(label, gold, from, to, color,
                System.currentTimeMillis(), Math.max(180L, durationMs)));
        if (activeCardAnimations.size() > 16) {
            activeCardAnimations.removeFirst();
        }
    }

    public void startPulse(TableEffectEvents.TableAnchor anchor, int color, float intensity, long durationMs) {
        activePulses.add(new AnchorPulseState(anchor, color, intensity, System.currentTimeMillis(),
                Math.max(180L, durationMs)));
        if (activePulses.size() > 12) {
            activePulses.removeFirst();
        }
    }

    public void showBanner(String text, int color, long durationMs) {
        activeBanner = new BannerState(text, color, System.currentTimeMillis(), Math.max(320L, durationMs));
    }

    // ── Accessors for Renderer ──────────────────────────────────

    public int drawPileSize() { return drawPileSize; }
    public int discardPileSize() { return discardPileSize; }
    public String topDiscardCardId() { return topDiscardCardId; }
    public String activePlayerUuid() { return activePlayerUuid; }
    public String phase() { return phase; }
    public String lastActionText() { return lastActionText; }
    public String seatAPlayerUuid() { return seatAPlayerUuid; }
    public String seatAPlayerName() { return seatAPlayerName; }
    public boolean seatAAlive() { return seatAAlive; }
    public int seatAHiddenIdentityCount() { return seatAHiddenIdentityCount; }
    public List<String> seatAIdentitySlots() { return seatAIdentitySlots; }
    public String seatBPlayerUuid() { return seatBPlayerUuid; }
    public String seatBPlayerName() { return seatBPlayerName; }
    public boolean seatBAlive() { return seatBAlive; }
    public int seatBHiddenIdentityCount() { return seatBHiddenIdentityCount; }
    public List<String> seatBIdentitySlots() { return seatBIdentitySlots; }
    public List<TableCardDisplay> tableCards() { return tableCards; }
    public List<ActiveCardAnimation> activeCardAnimations() { return activeCardAnimations; }
    public List<AnchorPulseState> activePulses() { return activePulses; }
    public BannerState activeBanner() { return activeBanner; }

    // ── Sync (BlockEntity Update Packet) ────────────────────────

    public void sync() {
        setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(getBlockPos());
            level.sendBlockUpdated(getBlockPos(), state, state, 2);
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── NBT Persistence ─────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("LinkedRoomId", linkedRoomId);
        tag.putInt("DrawPileSize", drawPileSize);
        tag.putInt("DiscardPileSize", discardPileSize);
        tag.putString("TopDiscardCardId", topDiscardCardId);
        tag.putString("ActivePlayerUuid", activePlayerUuid);
        tag.putString("Phase", phase);
        tag.putString("LastActionText", lastActionText);
        tag.putString("SeatAPlayerUuid", seatAPlayerUuid);
        tag.putString("SeatAPlayerName", seatAPlayerName);
        tag.putBoolean("SeatAAlive", seatAAlive);
        tag.putInt("SeatAHiddenIdentityCount", seatAHiddenIdentityCount);
        tag.put("SeatAIdentitySlots", saveStringList(seatAIdentitySlots));
        tag.putString("SeatBPlayerUuid", seatBPlayerUuid);
        tag.putString("SeatBPlayerName", seatBPlayerName);
        tag.putBoolean("SeatBAlive", seatBAlive);
        tag.putInt("SeatBHiddenIdentityCount", seatBHiddenIdentityCount);
        tag.put("SeatBIdentitySlots", saveStringList(seatBIdentitySlots));

        ListTag seated = new ListTag();
        for (UUID uuid : seatedPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Uuid", uuid);
            seated.add(entry);
        }
        tag.put("SeatedPlayers", seated);

        ListTag cards = new ListTag();
        for (TableCardDisplay card : tableCards) {
            cards.add(card.save());
        }
        tag.put("TableCards", cards);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        linkedRoomId = tag.getInt("LinkedRoomId");
        drawPileSize = tag.getInt("DrawPileSize");
        discardPileSize = tag.getInt("DiscardPileSize");
        topDiscardCardId = tag.getString("TopDiscardCardId");
        activePlayerUuid = tag.getString("ActivePlayerUuid");
        phase = tag.getString("Phase");
        lastActionText = tag.getString("LastActionText");
        seatAPlayerUuid = tag.getString("SeatAPlayerUuid");
        seatAPlayerName = tag.getString("SeatAPlayerName");
        seatAAlive = tag.getBoolean("SeatAAlive");
        seatAHiddenIdentityCount = tag.getInt("SeatAHiddenIdentityCount");
        loadStringList(tag.getList("SeatAIdentitySlots", Tag.TAG_STRING), seatAIdentitySlots);
        seatBPlayerUuid = tag.getString("SeatBPlayerUuid");
        seatBPlayerName = tag.getString("SeatBPlayerName");
        seatBAlive = tag.getBoolean("SeatBAlive");
        seatBHiddenIdentityCount = tag.getInt("SeatBHiddenIdentityCount");
        loadStringList(tag.getList("SeatBIdentitySlots", Tag.TAG_STRING), seatBIdentitySlots);

        seatedPlayers.clear();
        for (Tag entry : tag.getList("SeatedPlayers", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag compound) {
                seatedPlayers.add(compound.getUUID("Uuid"));
            }
        }

        tableCards.clear();
        for (Tag entry : tag.getList("TableCards", Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag compound) {
                tableCards.add(TableCardDisplay.load(compound));
            }
        }
    }

    private void applySeat(boolean seatAFlag, @Nullable SeatView seat) {
        if (seatAFlag) {
            seatAPlayerUuid = seat == null ? "" : seat.playerUuid();
            seatAPlayerName = seat == null ? "" : seat.playerName();
            seatAAlive = seat != null && seat.alive();
            seatAHiddenIdentityCount = seat == null ? 0 : seat.hiddenIdentityCount();
            seatAIdentitySlots.clear();
            if (seat != null) {
                seatAIdentitySlots.addAll(seat.identitySlots());
            }
            return;
        }
        seatBPlayerUuid = seat == null ? "" : seat.playerUuid();
        seatBPlayerName = seat == null ? "" : seat.playerName();
        seatBAlive = seat != null && seat.alive();
        seatBHiddenIdentityCount = seat == null ? 0 : seat.hiddenIdentityCount();
        seatBIdentitySlots.clear();
        if (seat != null) {
            seatBIdentitySlots.addAll(seat.identitySlots());
        }
    }

    private UUID playerUuidForZone(FourthRoomTableBlock.InteractionZone zone) {
        return switch (zone) {
            case SEAT_A -> seatAPlayerUuid.isBlank() ? null : UUID.fromString(seatAPlayerUuid);
            case SEAT_B -> seatBPlayerUuid.isBlank() ? null : UUID.fromString(seatBPlayerUuid);
            default -> null;
        };
    }

    private static ListTag saveStringList(List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(net.minecraft.nbt.StringTag.valueOf(value));
        }
        return list;
    }

    private static void loadStringList(ListTag list, List<String> out) {
        out.clear();
        for (Tag entry : list) {
            out.add(entry.getAsString());
        }
    }

    public record SeatView(String playerUuid, String playerName, boolean alive, int hiddenIdentityCount,
                           List<String> identitySlots) {
    }

    // ── Inner: Table Card Display Data ──────────────────────────

    public record TableCardDisplay(String titleText, String summaryText, int accentColor, boolean highlight,
                                   long timestamp) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("TitleText", titleText);
            tag.putString("SummaryText", summaryText);
            tag.putInt("AccentColor", accentColor);
            tag.putBoolean("Highlight", highlight);
            tag.putLong("Timestamp", timestamp);
            return tag;
        }

        public static TableCardDisplay load(CompoundTag tag) {
            return new TableCardDisplay(
                    tag.contains("TitleText") ? tag.getString("TitleText") : tag.getString("CardId"),
                    tag.contains("SummaryText") ? tag.getString("SummaryText") : tag.getString("PlayerName"),
                    tag.contains("AccentColor") ? tag.getInt("AccentColor") : 0xFFD7D7D7,
                    tag.contains("Highlight") && tag.getBoolean("Highlight"),
                    tag.getLong("Timestamp"));
        }
    }

    public record ActiveCardAnimation(String label, boolean gold, TableEffectEvents.TableAnchor from,
                                      TableEffectEvents.TableAnchor to, int color, long startTimeMs,
                                      long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float progress(long now) {
            return Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
        }
    }

    public record AnchorPulseState(TableEffectEvents.TableAnchor anchor, int color, float intensity, long startTimeMs,
                                   long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float progress(long now) {
            return Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
        }
    }

    public record BannerState(String text, int color, long startTimeMs, long durationMs) {
        public boolean expired(long now) {
            return now >= startTimeMs + durationMs;
        }

        public float alpha(long now) {
            float progress = Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
            float fadeIn = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((1.0F - progress) / 0.22F, 0.0F, 1.0F);
            return Math.min(fadeIn, fadeOut);
        }
    }
}
