package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TickTimer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.Pair;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 小偷大战
 * <p>
 * 模式特性：
 *  - 全员小偷
 *  - 胜利条件：活到最后或者攒够资金美美撤离
 *  - 模式附带随机事件提升游戏可玩性
 * </p>
 * <p>
 * 随机事件列表：
 *  - 物品雨：游玩区域内随机降下随机物品
 *  - 随机恋人事件：有人可能会被组为恋人
 *  - 转职事件：花费资金转职成其他中立
 *  - 佛说：你说我做，当没有佛说则是反向你说我做
 *  - 盗窃违法事件：当在摄像头前方一定范围内一定时间触发
 *  - 保安返厂：死亡的部分玩家复活为保安（根据在场人数），当玩家在60s内进行过偷窃行为则可被击杀，否则会导致小脑
 *  - 生成很值钱的宝石：拾取后出发追捕事件（复活几个保安追逐战），存活一定时间后给予资金，否则趋势；肘死那个人的保安会复活并继承他的资金
 * </p>
 * <p>
 * 特殊职业：
 *  - 随机卧底，当资金小于100被偷时化身保安
 * </p>
 */
public class SREThiefWarGameMode extends SREBaseCustomizationGameMode {
    /** 随机事件间隔范围 */
    public static final Pair<Integer, Integer> RANDOM_EVENT_INTERVAL = new Pair<>(15 * 20, 30 * 20 + 1);
    /** 随机画板数量比例范围: 根据此比例和人数计算数量 */
    public static final Pair<Float, Float> RANDOM_DRAWING_BOARD_PERCENTAGE = new Pair<>(0.1f, 0.4f);

    public static final int GLOWING_DURATION = 20 * 5;
    public static final int GLOWING_INTERVAL = 20 * 20;
    /**
     * @param identifier       游戏的id
     */
    public SREThiefWarGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
    }

    @Override
    protected void constructItemList() {
        sharedItems.add(TMMItems.CROWBAR::getDefaultInstance);
    }

    // 小偷模式可以摸尸体
    @Override
    public boolean canPickBodyContent() {
        return true;
    }
    @Override
    public boolean canSeeBodyContent() {
        return true;
    }
    @Override
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            gameWorldComponent.addRole(player, ModRoles.THIEF);
        }
    }

    /** 随机事件：随机在房间内生成几个画板 */
    protected void spawnDrawingBoardInRoom(ServerLevel serverWorld) {
        // 先确定存活玩家数再确定生成的画板数
        var allPlayers = serverWorld.players();
        List<ServerPlayer> players = new ArrayList<>();
        for (var p : allPlayers)
            if (!GameUtils.isPlayerEliminated(p))
                players.add(p);
        // 每次生成随机几个画板 : 限制其数量 > 0
        int boardNum = Math.max(1, serverWorld.random.nextInt((int) (players.size() * RANDOM_DRAWING_BOARD_PERCENTAGE.first),
                (int) (players.size() * RANDOM_DRAWING_BOARD_PERCENTAGE.second) + 1));

        // 记录所有房间位置 : id + pos
        List<Pair<Integer, Vec3>> allRoomPos = new ArrayList<>();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        int roomCount = areas.getRoomCount();
        for (int i = 1; i <= roomCount; ++i) {
            Vec3 pos = GameUtils.getSpawnPos(areas, i);
            if (pos != null){
                allRoomPos.add(new Pair<>(i, pos));
            }
        }

        Collections.shuffle(allRoomPos);
        // 保证画板数量不超过房间数量;
        boardNum = Math.min(boardNum, allRoomPos.size());
        // 打乱之后取前几个进行生成
        StringBuilder resultRoomIds = new StringBuilder();
        for (int i = 0; i < boardNum; ++i) {
            Vec3 pos = allRoomPos.get(i).second;
            // 生成物品
            serverWorld.addFreshEntity(
                new ItemEntity(serverWorld,pos.x,pos.y,pos.z,TMMItems.DRAWING_BOARD.getDefaultInstance())
            );
            resultRoomIds.append(allRoomPos.get(i).first).append(" ");
        }
        // 进行广播：通知生成了画板在这些房间
        for (ServerPlayer player : allPlayers){
            broadcastMessageTo(player, Component.translatable("message.gamemode.thief_war.tip_spawn_item",
                    resultRoomIds.toString()).withStyle(ChatFormatting.DARK_AQUA));
        }

    }
    @Override
    protected void initTickTimers(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        tickTimers.add(
                // 所有人间隔获得发光效果
                new TickTimer(GLOWING_INTERVAL, false, tickTimer -> {
                    for (ServerPlayer player : serverWorld.players()) {
                        if (GameUtils.isPlayerEliminated(player))
                            return;
                        // 刷新发光效果
                        if (player.hasEffect(MobEffects.GLOWING))
                            player.removeEffect(MobEffects.GLOWING);
                        player.addEffect(
                                new MobEffectInstance(
                                        MobEffects.GLOWING,  // 发光效果
                                        GLOWING_DURATION,                  // 持续时间（tick）
                                        1,
                                        false,                // 是否显示粒子效果
                                        false                  // 是否显示图标
                                ));
                    }
                })
        );
        // 随机事件计时器
        tickTimers.add(
                new TickTimer(RANDOM_EVENT_INTERVAL.second, false, tickTimer -> {
                    // 随机事件发生时生成画板
                    spawnDrawingBoardInRoom(serverWorld);
                    // 随机事件时间在范围内随机
                    tickTimer.setEndTime(serverWorld.random.nextInt(RANDOM_EVENT_INTERVAL.first, RANDOM_EVENT_INTERVAL.second));
                })
        );
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
                               List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        assignModdedRole(players, gameWorldComponent);

        // 初始化启动资金：获得目标的一半
        for (Player player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role == ModRoles.THIEF) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                ThiefPlayerComponent thief = ThiefPlayerComponent.KEY.get(player);
                shop.setBalance(thief.honorCost / 2);
            }
        }
    }
    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        // 统计小偷数量
        int thiefCount = 0;
        for (Player player : serverWorld.players()) {
            if (gameWorldComponent.isRole(player, ModRoles.THIEF) && !GameUtils.isPlayerEliminated(player)) {
                ++thiefCount;
            }
        }

        // 没有小偷存活则无人获胜
        if (thiefCount == 0) {
            winStatus = GameUtils.WinStatus.NO_PLAYER;
        }
        // 小偷数量为1则小偷获胜
        else if (thiefCount == 1) {
            RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.CUSTOM, "thief",
                    OptionalInt.of(new java.awt.Color(255, 215, 0).getRGB()));
        }
        else {
            Player winner = null;
            for (Player player : serverWorld.players()) {
                if (!GameUtils.isPlayerEliminated(player) && gameWorldComponent.isRole(player, ModRoles.THIEF)) {
                    ItemStack heldItem = player.getMainHandItem();
                    // 检查小偷是否手持小偷的荣誉（金锭）睡觉
                    if (heldItem.is(Items.GOLD_INGOT) && player.isSleeping()) {
                        winner = player;
                        break;
                    }
                }
            }
            if (winner != null) {
                for (Player player : serverWorld.players()) {
                    // 心脏麻痹其他活着的玩家
                    if (player != winner && !GameUtils.isPlayerEliminated(player))
                        GameUtils.killPlayer(player, true, winner,  Noellesroles.id("heart_attack"), true);
                }
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;
        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}
