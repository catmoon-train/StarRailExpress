package io.wifi.starrailexpress.api.replay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public class ClientGameReplayManager extends GameReplayManager {

    /**
     * 用于客户端，避免客户端调用后崩溃
     */
    public ClientGameReplayManager() {
        super();
    }

    @Override
    public void resetReplay() {
    }

    @Override
    public void initializeReplay(List<ServerPlayer> players, HashMap<UUID, SRERole> roles) {
    }

    @Override
    public void updateReplayInitialRoles(List<ServerPlayer> players, HashMap<UUID, SRERole> roles) {
    }

    @Override
    public void updateRolesFromComponent(io.wifi.starrailexpress.cca.SREGameWorldComponent component) {
    }

    @Override
    public void finalizeReplay(GameUtils.WinStatus winStatus, SREGameRoundEndComponent roundEndData) {
    }

    @Override
    public void recordPlayerName(Player player) {
    }

    @Override
    public void recordPlayerName(UUID uuid, String name) {
    }

    @Override
    public void recordPlayerNames(Map<UUID, String> playerNamesMap) {
    }

    @Override
    public boolean isPlayerNameRecorded(UUID uuid) {
        return false;
    }

    @Override
    public Map<UUID, String> getPlayerNames() {
        return new HashMap<>();
    }

    @Override
    public Component getPlayerName(UUID uuid) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
            String message) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
            String message, HolderLookup.Provider provider) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
            String message, boolean hidden) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
            String message, HolderLookup.Provider provider, boolean hidden) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component recordCustomEvent(Component msg) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component recordCustomEvent(Component msg, boolean hidden) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component recordPlayerKill(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public Component recordPlayerKill(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason, boolean hidden) {
        return Component.literal("ERROR: CLIENT.");
    }

    @Override
    public void recordPlayerRevival(UUID player, @Nullable SRERole role) {
    }

    @Override
    public void recordPlayerRevival(UUID player, @Nullable SRERole role, boolean hidden) {
    }

    @Override
    public void recordPlayerRoleChange(UUID player, SRERole oldRole, SRERole newRole) {
    }

    @Override
    public void recordPlayerRoleChange(UUID player, SRERole oldRole, SRERole newRole, boolean hidden) {
    }

    @Override
    public void recordStoreBuy(UUID playerUuid, ResourceLocation itemBought, int amount, int price) {
    }

    @Override
    public void recordStoreBuy(UUID playerUuid, ResourceLocation itemBought, int amount, int price, boolean hidden) {
    }

    @Override
    public void recordItemUse(UUID playerUuid, ResourceLocation itemUsed) {
    }

    @Override
    public void recordItemUse(UUID playerUuid, ResourceLocation itemUsed, boolean hidden) {
    }

    @Override
    public void breakArmor(UUID playerUuid) {
    }

    @Override
    public void recordSkillUsed(UUID playerUuid, ResourceLocation skillUsed) {
    }

    @Override
    public void recordSkillUsed(UUID playerUuid, ResourceLocation skillUsed, boolean hidden) {
    }

    /** 拆除炸弹/C4 成功：defuser 拆掉了 carrier 身上的炸弹（carrier 可为 null 表示拆除地面/方块电荷）。 */
    @Override
    public void recordBombDefuse(UUID defuserUuid, UUID carrierUuid) {
    }

    @Override
    public void recordBombDefuse(UUID defuserUuid, UUID carrierUuid, boolean hidden) {
    }

    /** 炸弹引爆/C4：sourceUuid 引爆（可为 null），victimUuid 被炸飞。 */
    @Override
    public void recordBombDetonate(UUID sourceUuid, UUID victimUuid) {
    }

    @Override
    public void recordBombDetonate(UUID sourceUuid, UUID victimUuid, boolean hidden) {
    }

    /** 玩家伪装：playerUuid 乔装改扮。 */
    @Override
    public void recordDisguise(UUID playerUuid) {
    }

    @Override
    public void recordDisguise(UUID playerUuid, boolean hidden) {
    }

    /** 触发陷阱：victimUuid 踩中了 trapperUuid（设陷者，可为 null）布下的陷阱。 */
    @Override
    public void recordTrapTriggered(UUID trapperUuid, UUID victimUuid) {
    }

    @Override
    public void recordTrapTriggered(UUID trapperUuid, UUID victimUuid, boolean hidden) {
    }

    /** 撬门：playerUuid 用撬棍撬开了 pos 处的门。 */
    @Override
    public void recordDoorPry(UUID playerUuid, BlockPos pos) {
    }

    /** 上锁：playerUuid 用锁具封锁了 pos 处的门。 */
    @Override
    public void recordDoorSeal(UUID playerUuid, BlockPos pos) {
    }

    /** 绳索拉回：roperUuid 用绳索命中并拽回了 pulledUuid。 */
    @Override
    public void recordRopePull(UUID roperUuid, UUID pulledUuid) {
    }

    @Override
    public void recordRopePull(UUID roperUuid, UUID pulledUuid, boolean hidden) {
    }

    @Override
    public void setPlayerCount(int count) {
    }

    @Override
    public void setCivilianPlayers(java.util.List<UUID> players) {
    }

    @Override
    public void setKillerPlayers(java.util.List<UUID> players) {
    }

    @Override
    public void setVigilantePlayers(java.util.List<UUID> players) {
    }

    @Override
    public void setLooseEndPlayers(java.util.List<UUID> players) {
    }

    @Override
    public void setWinningPlayer(UUID player) {
    }

    @Override
    public void setWinningTeam(String team) {
    }

    @Override
    public GameReplay getCurrentReplay() {
        return null;
    }

    @Override
    public ReplaySession getSession() {
        return null;
    }

    @Override
    public List<ReplayTimelineEvent> getTimelineEvents(boolean includeHidden) {
        return List.of();
    }

    @Override
    public Component generateScreenReplay(int maxLines) {
        return Component.literal("ERROR: CLIENT");
    }

    @Override
    public List<Component> generateScreenReplayLines(int maxLines) {
        return List.of();
    }

    @Override
    public void recordEvent(ReplayEventTypes.EventType eventType, ReplayEventTypes.EventDetails details) {
    }

    @Override
    public void recordEvent(ReplayEventTypes.EventType eventType, ReplayEventTypes.EventDetails details,
            boolean hidden) {
    }

    @Override
    public void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message) {
    }

    @Override
    public void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message, boolean hidden) {
    }

    @Override
    public List<ReplayEvent> getEvents() {
        return List.of();
    }

    @Override
    public List<ReplayEvent> getEventsInTimeRange(long startTime, long endTime) {
        return List.of();
    }

    @Override
    public List<ReplayEvent> getEventsByPlayer(UUID playerUuid) {
        return List.of();
    }

    @Override
    public List<ReplayEvent> getEventsByType(ReplayEventTypes.EventType eventType) {
        return List.of();
    }

    @Override
    public List<UUID> getAllPlayerUuids() {
        return List.of();
    }

    @Override
    public Optional<String> getPlayerNameOptional(UUID playerUuid) {
        return Optional.empty();
    }

    @Override
    public void saveReplay() {
    }

    @Override
    public GameReplayData loadReplay() {
        SRE.LOGGER.error("Failed to load game replay: Client");
        return null;
    }

    public static void sendSystemMessage(ServerPlayer player, Component message) {
    }

    @Override
    public Component generateReplay() {
        return Component.literal("ERROR: CLIENT");

    }

    @Override
    public Component generateReplay(boolean includeHidden) {
        return Component.literal("ERROR: CLIENT");

    }

    @Override
    public void recordItemEatFlaggedItem(Player player, Item item, String flagName) {
    }

    @Override
    public Component recordPlayerNotKilled(Player killer, Player victim, ResourceLocation deathReason) {
        return Component.literal("ERROR: CLIENT");
    }
}
