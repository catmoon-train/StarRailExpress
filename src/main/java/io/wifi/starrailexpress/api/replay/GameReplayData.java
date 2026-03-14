package io.wifi.starrailexpress.api.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ArmorBreakDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.BlackoutEventDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ChangeRoleDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.DoorActionDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.GrenadeThrownDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.ItemUsedDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.LockpickAttemptDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.MoodChangeDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerJoinLeaveDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerKillDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerPoisonedDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PlayerRevivalDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.PsychoStateChangeDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.StoreBuyDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.TaskCompleteDetails;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class GameReplayData {
    private int playerCount;
    private List<UUID> civilianPlayers;
    private List<UUID> killerPlayers;
    private List<UUID> vigilantePlayers;
    private List<UUID> looseEndPlayers;
    private UUID winningPlayer;
    private String winningTeam;
    private final List<ReplayEvent> timeline;
    private Map<UUID, String> playerRoles;
    public MutableComponent winningTitle = null;

    public GameReplayData() {
        this.playerCount = 0;
        this.civilianPlayers = new ArrayList<>();
        this.killerPlayers = new ArrayList<>();
        this.vigilantePlayers = new ArrayList<>();
        this.looseEndPlayers = new ArrayList<>();
        this.timeline = new ArrayList<>();
        this.playerRoles = new HashMap<>();
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public void setCivilianPlayers(List<UUID> civilianPlayers) {
        this.civilianPlayers = civilianPlayers;
    }

    public void setKillerPlayers(List<UUID> killerPlayers) {
        this.killerPlayers = killerPlayers;
    }

    public void setVigilantePlayers(List<UUID> vigilantePlayers) {
        this.vigilantePlayers = vigilantePlayers;
    }

    public void setLooseEndPlayers(List<UUID> looseEndPlayers) {
        this.looseEndPlayers = looseEndPlayers;
    }

    public void setWinningPlayer(UUID winningPlayer) {
        this.winningPlayer = winningPlayer;
    }

    public void setWinningTeam(String winningTeam) {
        this.winningTeam = winningTeam;
    }

    public void addEvent(ReplayEvent event) {

        this.timeline.add(event);
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public List<UUID> getCivilianPlayers() {
        return civilianPlayers;
    }

    public List<UUID> getKillerPlayers() {
        return killerPlayers;
    }

    public List<UUID> getVigilantePlayers() {
        return vigilantePlayers;
    }

    public List<UUID> getLooseEndPlayers() {
        return looseEndPlayers;
    }

    public UUID getWinningPlayer() {
        return winningPlayer;
    }

    public MutableComponent getWinningTitle() {
        if (winningTitle == null) {
            return Component.translatable("announcement.star.win." + getWinningTeam().toLowerCase());
        }
        return winningTitle;
    }

    public String getWinningTeam() {
        return winningTeam;
    }

    public List<ReplayEvent> getTimeline() {
        return timeline;
    }

    public Map<UUID, String> getPlayerRoles() {
        return playerRoles;
    }

    public void setPlayerRoles(Map<UUID, String> playerRoles) {
        this.playerRoles = playerRoles;
    }

    public static final Map<ResourceLocation, Item> DEATH_REASON_TO_ITEM = new HashMap<>();

    static {
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.BAT, TMMItems.BAT);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.REVOLVER, TMMItems.REVOLVER);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.DERRINGER, TMMItems.DERRINGER);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.KNIFE, TMMItems.KNIFE);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.GRENADE, TMMItems.GRENADE);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.POISON, TMMItems.POISON_VIAL);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.ARROW, Items.ARROW);
        DEATH_REASON_TO_ITEM.put(GameConstants.DeathReasons.TRIDENT, Items.TRIDENT);
        // 注意：FELL_OUT_OF_TRAIN 和 GENERIC 没有对应物品
    }

    public Component toText(GameReplayManager manager, GameReplayData replayData,
            io.wifi.starrailexpress.api.replay.ReplayEvent event) {
        if (event == null)
            return null;
        UUID sourcePlayer = null;
        UUID targetPlayer = null;
        Component itemUsedText = null;
        String message = null;
        Component Role_1 = null;
        Component Role_2 = null;
        // 根据 EventDetails 类型提取信息
        if (event
                .details() instanceof PlayerKillDetails(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason)) {
            sourcePlayer = killerUuid;
            targetPlayer = victimUuid;
            itemUsedText = GameReplayUtils.getItemDisplayName(deathReason);
        } else if (event.details() instanceof PlayerPoisonedDetails(UUID poisonerUuid, UUID victimUuid)) {
            sourcePlayer = poisonerUuid;
            targetPlayer = victimUuid;
            itemUsedText = GameReplayUtils.getItemDisplayName(GameConstants.DeathReasons.POISON); // 假设中毒事件使用毒药物品
        } else if (event.details() instanceof TaskCompleteDetails(UUID playerUuid, ResourceLocation taskId)) {
            sourcePlayer = playerUuid;
            itemUsedText = Component.translatable("sre.task." + taskId.getPath());
        } else if (event.details() instanceof StoreBuyDetails(UUID playerUuid, ResourceLocation itemId, int cost)) {
            sourcePlayer = playerUuid;
            itemUsedText = GameReplayUtils.getItemDisplayName(itemId);
            message = String.valueOf(cost);
        } else if (event.details() instanceof DoorActionDetails details) {
            sourcePlayer = details.playerUuid();
            message = String.valueOf(details.doorPos()); // 可以考虑更友好的门位置显示
        } else if (event.details() instanceof LockpickAttemptDetails details) {
            sourcePlayer = details.playerUuid();
            message = String.valueOf(details.success());
        } else if (event.details() instanceof ItemUsedDetails(UUID playerUuid, ResourceLocation itemId)) {
            sourcePlayer = playerUuid;
            itemUsedText = GameReplayUtils.getItemDisplayName(itemId);
        } else if (event.details() instanceof MoodChangeDetails(UUID playerUuid, int oldMood, int newMood)) {
            sourcePlayer = playerUuid;
            message = String.format("%d -> %d", oldMood, newMood);
        } else if (event.details() instanceof PsychoStateChangeDetails(UUID playerUuid, int oldState, int newState)) {
            sourcePlayer = playerUuid;
            message = String.format("%d -> %d", oldState, newState);
        } else if (event.details() instanceof BlackoutEventDetails(long duration)) {
            message = String.valueOf(duration);
        } else if (event
                .details() instanceof GrenadeThrownDetails(UUID playerUuid, net.minecraft.core.BlockPos position)) {
            sourcePlayer = playerUuid;
            message = String.valueOf(position);
        } else if (event.details() instanceof ChangeRoleDetails roleDetail) {
            sourcePlayer = roleDetail.player();
            Role_1 = GameReplayUtils.getRoleNameWithSourceTMMColor(roleDetail.oldRole());
            Role_2 = GameReplayUtils.getRoleNameWithSourceTMMColor(roleDetail.newRole());
            // message = ;
        } else if (event.details() instanceof PlayerRevivalDetails revivalDetails) {
            sourcePlayer = revivalDetails.player();
            Role_1 = GameReplayUtils.getRoleNameWithSourceTMMColor(revivalDetails.Role());
            // message = ;
        } else if (event.details() instanceof ArmorBreakDetails ambd) {
            sourcePlayer = ambd.playerUuid();
            // message = ;
        } else if (event.details() instanceof PlayerJoinLeaveDetails pd) {
            sourcePlayer = pd.player();
            message = pd.scoreboardName();
            // message = ;
        }

        Component sourceName = sourcePlayer != null ? manager.getPlayerName(sourcePlayer)
                : Component.translatable("sre.replay.event.unknown_player").withStyle(ChatFormatting.OBFUSCATED)
                        .withStyle(ChatFormatting.GRAY);
        Component targetName = targetPlayer != null ? manager.getPlayerName(targetPlayer)
                : Component.translatable("sre.replay.event.unknown_player").withStyle(ChatFormatting.OBFUSCATED)
                        .withStyle(ChatFormatting.GRAY);

        // 获取角色信息并设置颜色
        sourceName = GameReplayUtils.getReplayPlayerDisplayText(sourcePlayer, manager, replayData, false);
        targetName = GameReplayUtils.getReplayPlayerDisplayText(targetPlayer, manager, replayData, true);

        return switch (event.eventType()) {
            // 主要事件
            case PLAYER_KILL -> {
                if (sourceName != null) {
                    yield Component.translatable("sre.replay.event.kill", sourceName, itemUsedText, targetName);
                } else {
                    // 如果没有杀手（例如意外死亡），则使用不同的翻译键
                    yield Component.translatable("sre.replay.event.kill_no_killer", targetName, itemUsedText);
                }
            }
            case PLAYER_POISONED -> {
                if (sourceName != null) {
                    yield Component.translatable("sre.replay.event.poison", sourceName, itemUsedText, targetName);
                } else {
                    // 如果没有下毒者（例如意外中毒），则使用不同的翻译键
                    yield Component.translatable("sre.replay.event.poison_no_killer", targetName, itemUsedText);
                }
            }
            case ARMOR_BREAK -> Component.translatable("sre.replay.event.armor_break", sourceName);
            case GRENADE_THROWN -> Component.translatable("sre.replay.event.grenade_thrown", sourceName);
            case ITEM_USED -> Component.translatable("sre.replay.event.skill_used", sourceName, itemUsedText);
            case BLACKOUT_START ->
                Component.translatable("sre.replay.event.blackout_start", Component.nullToEmpty(message));
            case BLACKOUT_END -> Component.translatable("sre.replay.event.blackout_end");
            // 系统事件
            case GAME_START -> Component.translatable("sre.replay.event.game_start").withStyle(ChatFormatting.GREEN);
            case GAME_END -> Component
                    .translatable("sre.replay.event.game_end",
                            replayData.getWinningTitle()
                                    .withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GREEN);
            case PLAYER_JOIN -> {
                if (sourceName != null) {
                    yield Component.translatable("sre.replay.event.player_join", sourceName)
                            .withStyle(ChatFormatting.GRAY);
                } else {
                    if (message != null) {
                        yield Component
                                .translatable("sre.replay.event.player_join",
                                        Component.literal(message).withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY);
                    } else {
                        yield Component
                                .translatable("sre.replay.event.player_join",
                                        Component.translatable("sre.replay.event.unknown_player"))
                                .withStyle(ChatFormatting.GRAY);
                    }
                }
            }
            case PLAYER_LEAVE -> {
                if (sourceName != null) {
                    yield Component.translatable("sre.replay.event.player_leave", sourceName)
                            .withStyle(ChatFormatting.GRAY);
                } else {
                    if (message != null) {
                        yield Component
                                .translatable("sre.replay.event.player_leave",
                                        Component.literal(message).withStyle(ChatFormatting.GRAY))
                                .withStyle(ChatFormatting.GRAY);
                    } else {
                        yield Component
                                .translatable("sre.replay.event.player_leave",
                                        Component.translatable("sre.replay.event.unknown_player"))
                                .withStyle(ChatFormatting.GRAY);
                    }
                }
            }
            case DOOR_LOCK -> {
                // yield Component.translatable("sre.replay.event.door_lock", sourceName,
                // message);
                yield null;
            }
            case DOOR_UNLOCK -> {
                // yield Component.translatable("sre.replay.event.door_unlock", sourceName,
                // message);
                yield null;
            }
            case TASK_COMPLETE, LOCKPICK_ATTEMPT, DOOR_CLOSE, DOOR_OPEN, STORE_BUY, MOOD_CHANGE,
                    PSYCHO_STATE_CHANGE ->
                null;
            case CHANGE_ROLE -> {
                yield Component.translatable("sre.replay.event.change_role", sourceName, Role_1, Role_2);
            }
            case PLAYER_REVIVAL -> {
                yield Component.translatable("sre.replay.event.player_revival", sourceName, Role_1);
            }
            // 次要事件

            /*
             * case DOOR_OPEN -> Component.translatable("sre.replay.event.door_open",
             * sourceName);
             * case DOOR_CLOSE -> Component.translatable("sre.replay.event.door_close",
             * sourceName);
             * case LOCKPICK_ATTEMPT ->
             * Component.translatable("sre.replay.event.lockpick_attempt", sourceName,
             * Boolean.parseBoolean(message) ?
             * Component.translatable("sre.replay.event.success").withStyle(ChatFormatting.
             * GREEN) :
             * Component.translatable("sre.replay.event.failed").withStyle(ChatFormatting.
             * RED));
             * case TASK_COMPLETE ->
             * Component.translatable("sre.replay.event.task_complete", sourceName,
             * itemUsedText);
             * case STORE_BUY -> {
             * Component costComponent = message != null ? Component.nullToEmpty(message) :
             * Component.nullToEmpty("?");
             * yield Component.translatable("sre.replay.event.store_buy", sourceName,
             * itemUsedText, costComponent);
             * }
             * case MOOD_CHANGE -> Component.translatable("sre.replay.event.mood_change",
             * sourceName, Component.nullToEmpty(message));
             * case DOOR_LOCK -> Component.translatable("sre.replay.event.door_lock",
             * sourceName, Component.nullToEmpty(message));
             * case DOOR_UNLOCK -> Component.translatable("sre.replay.event.door_unlock",
             * sourceName, Component.nullToEmpty(message));
             * case PSYCHO_STATE_CHANGE ->
             * Component.translatable("sre.replay.event.psycho_state_change", sourceName,
             * Component.nullToEmpty(message));
             */
            case CUSTOM_EVENT -> {
                if (event
                        .details() instanceof ReplayEventTypes.CustomEventDetails(Component msg)) {
                    if (msg != null) {
                        yield msg;
                    }
                    yield Component.translatable("sre.replay.event.custom_event",
                            Component.nullToEmpty("Unknown Event"));
                }
                yield Component.translatable("sre.replay.event.custom_event", Component.nullToEmpty("Unknown Event"));
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + event.eventType());
        };
    }

    public enum EventType {
        GAME_START,
        GAME_END,
        PLAYER_JOIN,
        PLAYER_LEAVE,
        PLAYER_KILL,
        ARMOR_BREAK,
        PLAYER_POISONED,
        GRENADE_THROWN,
        SKILL_USED,
        DOOR_OPEN,
        DOOR_CLOSE,
        LOCKPICK_ATTEMPT,
        TASK_COMPLETE,
        STORE_BUY,
        MOOD_CHANGE,
        CUSTOM_MESSAGE,
        ROLE_ASSIGNMENT,
        DOOR_LOCK,
        DOOR_UNLOCK,
        ITEM_USED,
        PSYCHO_STATE_CHANGE,
        BLACKOUT_START,
        BLACKOUT_END, CHANGE_ROLE, PLAYER_REVIVAL
    }

    public static class ReplayEvent {
        public final EventType type;
        public final UUID sourcePlayer;
        public final UUID targetPlayer;
        public final String itemUsed;
        public final String message;
        public final long timestamp;
        public final String text_a;
        public final String text_b;

        public ReplayEvent(EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed, String message) {
            this(type, sourcePlayer, targetPlayer, itemUsed, message, "", "");
        }

        public ReplayEvent(EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed, String message,
                String text_a, String text_b) {
            this.type = type;
            this.sourcePlayer = sourcePlayer;
            this.targetPlayer = targetPlayer;
            this.itemUsed = itemUsed;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.text_a = text_a;
            this.text_b = text_b;
        }

        public EventType getType() {
            return type;
        }

        public UUID getSourcePlayer() {
            return sourcePlayer;
        }

        public UUID getTargetPlayer() {
            return targetPlayer;
        }

        public String getItemUsed() {
            return itemUsed;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public void setWinningTitle(Component customWinnerTitle) {
        if (customWinnerTitle != null) {
            this.winningTitle = customWinnerTitle.copy();
        } else {
            this.winningTitle = null;
        }
    }
}