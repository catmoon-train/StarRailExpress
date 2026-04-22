package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * 轮盘赌锦标赛
 * <p>
 *     - 模式特性：玩家两两分组先后进行多轮赛
 *          每轮后可购买道具（根据本轮剩余生命值获得金币）：包括局内道具（便宜，仅对局内使用增加获胜可能性）和场外道具（较贵，如一次性手枪直接打死对手相当于掀桌子）
 *     - 局内死亡条件：生命值不足将死亡，或被对手使用场外道具击杀
 *     - 局外死亡条件：死亡次数累计到一定值死亡旁观
 * </p>
 */
public class SREDevilRouletteGameMode extends GameMode {
    /**
     * @param identifier       the game mode identifier
     */
    public SREDevilRouletteGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
        initModeItems();
    }
    protected void initModeItems() {
        devilRouletteItems.add(() -> new ItemStack(TMMItems.DEFENSE_VIAL));
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        reset();
        checkCanTableUse();
        initRoles(players, gameWorldComponent);
        initPlayerItems(players, gameWorldComponent);
        sendWelcomePackets(players, gameWorldComponent, SpecialGameModeRoles.DIRT);

        addAllPlayers(players);
        assignMatch(serverWorld);
        convenePlayers();
    }

    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players)
            gameWorldComponent.addRole(player, SpecialGameModeRoles.DIRT);
    }
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            // 添加模式专属物品
            for (Supplier<ItemStack> itemSupplier : devilRouletteItems) {
                ItemStack itemStack = itemSupplier.get();
                if (itemStack != null && !itemStack.isEmpty()) {
                    player.addItem(itemStack);
                }
            }
        }
    }
    protected void checkCanTableUse() {
        rouletteTableEntities.removeIf(table -> table.isRemoved() || table.getLevel() == null);
    }
    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
                                      SRERole role) {
        if (role == null)
            return;
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player,
                    new AnnounceWelcomePayload(role.identifier().toString(), -1, -1));
        }
    }
    /** 为玩家队列分配桌子 */
    private void assignPlayerQueue(Queue<UUID> players, ServerLevel serverWorld) {
        // 分配正常组
        // 至少要两名玩家 + 1张桌子 才能生成一个对局
        while (!usableTableEntities.isEmpty() && !players.isEmpty()) {
            DevilRouletteTableEntity tableEntity = usableTableEntities.poll();
            UUID id = players.poll();
            if (id == null)
                continue;
            Player player1 = serverWorld.getPlayerByUUID(id);
            if (player1 == null) {
                continue;
            }
            boolean isPlayer2Null = true;
            while (!players.isEmpty()) {
                UUID id2 = players.poll();
                if (id2 == null)
                    continue;
                Player player2 = serverWorld.getPlayerByUUID(id2);
                if (player2 != null) {
                    player1.displayClientMessage(
                            Component.translatable("noellesroles.game.devil_roulette.tip.play_with",
                                            player2.getDisplayName().getString())
                                    .withStyle(ChatFormatting.BLUE),true);
                    player2.displayClientMessage(
                            Component.translatable("noellesroles.game.devil_roulette.tip.play_with",
                                            player1.getName().getString())
                                    .withStyle(ChatFormatting.BLUE),true);

                    // 玩家和桌子都存在则分配对局
                    tableEntity.assignMatch(player1, player2);
                    tableEntity.convenePlayers();
                    isPlayer2Null = false;
                    break;
                }
            }
            // 如果玩家1不为空，玩家2为空，则将玩家1加入winners（预备队列）中
            if (isPlayer2Null)
                winners.add(player1.getUUID());
        }
        // 将之后的玩家添加到winners中轮空进入下一轮
        while (!players.isEmpty()) {
            // 没有足够的桌子分配玩家或剩余1个玩家，剩余玩家等待下一轮
            Player player = serverWorld.getPlayerByUUID(players.poll());
            if (player != null){
                player.displayClientMessage(
                        Component.translatable("noellesroles.game.devil_roulette.tip.wait_player")
                                .withStyle(ChatFormatting.WHITE),true);
                winners.add(player.getUUID());
            }
        }
    }
    protected void assignMatch(ServerLevel serverWorld) {
        updateUsableTableEntities();
        removeAllIfPlayerEliminated(serverWorld);
        assignPlayerQueue(currentPlayers, serverWorld);
        assignPlayerQueue(losers, serverWorld);
    }

    /** 将玩家召集到桌子上对局 */
    protected void convenePlayers() {
        for (DevilRouletteTableEntity tableEntity : rouletteTableEntities) {
            if (tableEntity.isGameActive())
                tableEntity.convenePlayers();
        }
    }

    protected void updateUsableTableEntities() {
        usableTableEntities.clear();
        for (DevilRouletteTableEntity tableEntity : rouletteTableEntities) {
            if (!tableEntity.isGameActive()) {
                usableTableEntities.add(tableEntity);
            }
        }
    }

    protected void addAllPlayers(List<ServerPlayer> players) {
        List<UUID> playerIDs = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (!GameUtils.isPlayerEliminated(player) && !currentPlayers.contains(player.getUUID()))
                playerIDs.add(player.getUUID());
        }
        Collections.shuffle(playerIDs);
        currentPlayers.addAll(playerIDs);
    }
    private void addAllPlayerId(@NotNull List<UUID> playerIDs) {
        Collections.shuffle(playerIDs);
        currentPlayers.addAll(playerIDs);
        playerIDs.clear();
    }

    /** 移除所有淘汰的玩家 */
    public void removeAllIfPlayerEliminated(ServerLevel level) {
        currentPlayers.removeIf(playerID -> playerID == null || GameUtils.isPlayerEliminated(level.getPlayerByUUID(playerID)));
        winners.removeIf(playerID -> playerID == null || GameUtils.isPlayerEliminated(level.getPlayerByUUID(playerID)));
        losers.removeIf(playerID -> playerID == null || GameUtils.isPlayerEliminated(level.getPlayerByUUID(playerID)));
    }
    public void addWinner(@NotNull Player player) {
        if (!winners.contains(player.getUUID()))
            winners.add(player.getUUID());
    }
    public void addLooser(@NotNull Player player) {
        if (!losers.contains(player.getUUID()))
            losers.add(player.getUUID());
    }

    public void addRouletteTableEntity(DevilRouletteTableEntity rouletteTableEntity) {
        if (!rouletteTableEntities.contains(rouletteTableEntity))
            rouletteTableEntities.add(rouletteTableEntity);
    }
    public List<DevilRouletteTableEntity> getRouletteTableEntities() {
        return rouletteTableEntities;
    }

    @Override
    public boolean hasSafeTime() {
        return false;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        if (curAssignTick++ >= ASSIGN_INTERVAL) {
            addAllPlayerId(winners);
            assignMatch(serverWorld);
            curAssignTick = 1;
        }

        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        int playerCounter = 0;
        for (ServerPlayer player : serverWorld.players()) {
            // check if some civilians are still alive
            if (!GameUtils.isPlayerEliminated(player)) {
                ++playerCounter;
            }
        }

        // check killer win condition (killed all civilians)
        if (playerCounter <= 1) {
            winStatus = GameUtils.WinStatus.GAMBLER;
            var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
            roundEnd.CustomWinnerID = "gambler";
            for (ServerPlayer player : serverWorld.players()) {
                if (!GameUtils.isPlayerEliminated(player)) {
                    roundEnd.CustomWinnerPlayers.add(player.getUUID());
                }
            }
        }

        // check if out of timea
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
            for (DevilRouletteTableEntity rouletteTableEntity : rouletteTableEntities)
                rouletteTableEntity.reset();
        }
    }

    protected void reset() {
        winners.clear();
        currentPlayers.clear();
        losers.clear();
        usableTableEntities.clear();
    }

    /**  自动分配间隔 */
    public static final int ASSIGN_INTERVAL = 1200;
    /** 游戏对局结束每点生命值转化的金币数 */
    public static final int WINNER_COIN = 100;
    protected final List<Supplier<ItemStack>> devilRouletteItems = new ArrayList<>();
    protected final List<DevilRouletteTableEntity> rouletteTableEntities = new ArrayList<>();
    protected final List<UUID> winners = new ArrayList<>();
    /** 如果桌子不够分配，则由该指针指向未分配的玩家头 */
    protected final Queue<UUID> currentPlayers = new ArrayDeque<>();
    protected final Queue<UUID> losers = new ArrayDeque<>();
    protected final Queue<DevilRouletteTableEntity> usableTableEntities = new ArrayDeque<>();
    /** 当前分配间隔tick */
    protected int curAssignTick = 0;
};