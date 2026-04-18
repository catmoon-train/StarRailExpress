package org.agmas.noellesroles.mini_gme;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Supplier;

public class DevilRouletteGame {
    public static final List<Supplier<ItemStack>> rouletteItems = new ArrayList<>();
    public static final int START_ITEM_NUMBER = 3;
    public static final int START_HEALTH = 3;
    public static final int GUN_BULLET_SLOT_NUMBER = 6;
    static {
    }
    /**
     * 游戏属于的游戏模式
     * <p>
     *     根据不同模式决定游戏行为，如：
     *     Lobby/default：默认方式运行（如在大厅中时开局刷新道具，而轮盘赌模式只能自行购买）
     * </p>
     */
    public enum GameMode {
        /** 大厅模式:default */
        Lobby,
        /** 轮盘赌模式 */
        Roulette,
    }
    public enum Target {
        /** 自己 */
        self,
        /** 对方 */
        opposite,
    }
    public static class FireResult {
        /** 是否是真弹 */
        public boolean isTrueBullet = false;
        /** 是否重装弹（当子弹打空后返回true） */
        public boolean isReload = false;
        /** 目标是否存活 */
        public boolean isTargetAlive = true;
        /** 是否切换操作者 */
        public boolean isSwitch = false;
    }
    public static class GamePlayerData {
        GamePlayerData(Player player) {
            this.player = player;
        }
        public int getHealth() {
            return health;
        }
        public Player getPlayer() {
            return player;
        }
        protected Player player;
        protected int health = START_HEALTH;
    }
    public DevilRouletteGame(Player player1, Player player2, RandomSource random) {
        playerDataList = new ArrayList<>();
        playerDataList.add(new GamePlayerData(player1));
        playerDataList.add(new GamePlayerData(player2));
        currentPlayerData = playerDataList.getFirst();
        this.random = random;
    }
    public void init() {

    }
    public void start() {
        random.nextInt(2);
        // 开局随机选择一个玩家启动
        currentPlayerData = playerDataList.get(random.nextInt(2));
        switch (gameMode) {
            case Roulette -> {
                break;
            }
            default -> {
                int startItemNumber = 3;
                for (int i = 0; i < startItemNumber; ++i){
                    for (int j = 0; j < 2; ++j) {
                        GamePlayerData playerData = playerDataList.get(j);
                        if (!rouletteItems.isEmpty()){
                            playerData.player.addItem(rouletteItems.get(random.nextInt(rouletteItems.size())).get());
                        }
                    }
                }
                break;
            }
        }
        reloadBullet();
    }
    /**
     * 开火操作
     * @param target 操作目标
     * @return 弹丸结果
     */
    public FireResult fire(Target target) {
        FireResult result = new FireResult();
        GamePlayerData targetPlayerData = playerDataList.get(indexOfResult(currentPlayerData.player, target));
        Boolean resultBullet = bulletList.poll();
        result.isTrueBullet = Boolean.TRUE.equals(resultBullet);
        if(Boolean.TRUE.equals(resultBullet)) {
            --targetPlayerData.health;
            if (targetPlayerData.health <= 0) {
                result.isTargetAlive = false;
                isGameEnd = true;
                for (GamePlayerData playerData : playerDataList) {
                    if (playerData.health > 0)
                        winner = playerData;
                }
            }
        }

        // 当子弹列表为空时，重新加载子弹
        if (bulletList.isEmpty()) {
            reloadBullet();
            result.isReload = true;
        }

        // 将操作权交给选择目标
        if (currentPlayerData != targetPlayerData) {
            currentPlayerData = targetPlayerData;
            result.isSwitch = true;
        }
        return result;
    }
    public boolean canOperate(Player player) {
        return player == currentPlayerData.player;
    }
    public void reloadBullet() {
        bulletList.clear();
        List<Boolean> newBulletList = new ArrayList<>();
        // 添加实弹和虚弹
        trueBulletNumber = random.nextInt(1,GUN_BULLET_SLOT_NUMBER);
        for (int i = 0; i < GUN_BULLET_SLOT_NUMBER; ++i) {
            newBulletList.add(i < trueBulletNumber);
        }
        // 打乱实弹虚弹
        Collections.shuffle(newBulletList);
        bulletList.addAll(newBulletList);
    }
    public int indexOfResult(Player player, Target target) {
        if (playerDataList.getFirst().player ==  player) {
            // 如果操作玩家是玩家1，且目标为自己，则返回索引0
            return target == Target.self ? 0 : 1;
        }
        // 如果操作玩家是玩家2，且目标为自己，则返回索引1
        return target == Target.self ? 1 : 0;
    }
    public boolean isGameEnd() {
        return isGameEnd;
    }
    public int getHealth(Player player) {
        for (GamePlayerData playerData : playerDataList)
            if (playerData.player == player)
                return playerData.health;
        return 0;
    }
    public int getTrueBulletNumber() {
        return trueBulletNumber;
    }
    public Queue<Boolean> getBulletList() {
        return bulletList;
    }
    public GamePlayerData getWinner() {
        return winner;
    }
    public void setRandom(RandomSource random) {
        this.random = random;
    }

    protected List<GamePlayerData> playerDataList;
    /** 弹丸列表 */
    protected Queue<Boolean> bulletList = new ArrayDeque<>();
    protected RandomSource random;
    /** 当前操作玩家 */
    protected GamePlayerData currentPlayerData;
    protected GamePlayerData winner = null;
    protected GameMode gameMode = GameMode.Lobby;
    protected boolean isGameEnd = false;
    protected int trueBulletNumber;
}
