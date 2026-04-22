package org.agmas.noellesroles.mini_gme;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;
import java.util.function.Supplier;

/**
 * 轮盘赌游戏类
 * <p>
 *     场外道具列表：
 *      - 一次性手枪：打死同桌对手
 *      - 毒药：给椅子下毒（趁对方离开做任务）
 *      - 时停钟：时停偷隔壁桌道具
 *      - 阴谋之书页：猜测对方手中是否持有某道具，有则对方心脏麻痹而死
 * </p>
 * <p>
 *     局内道具列表：
 *      - 放大镜：查看下一发子弹是否是实弹
 *      - 口香糖：回复一点生命
 *      - 弹夹：重新换弹
 *      - 钢珠：下一发如果为实弹则造成伤害+1
 *      - 反转卡：实弹转为虚弹，虚弹转为实弹
 *      - 手铐：多操作一回合
 *      - 电话：得知随机一枚实弹信息（该轮内第i发为实弹），不会显示已发射子弹，如果没有实弹也会告知
 * </p>
 */
public class DevilRouletteGame {
    // 物品列表
    public static final List<Item> ROULETTE_ITEMS = List.of(
            ModItems.MAGNIFYING_GLASS,
            ModItems.CHEWING,
            ModItems.CLIP,
            ModItems.STEEL_BALL,
            ModItems.REVERSING_CARD,
            ModItems.TELEPHONE
    );
    public static final List<Supplier<ItemStack>> rouletteItems = new ArrayList<>();
    public static final GamePlayerData NONE_PLAYER = new GamePlayerData(UUID.randomUUID());
    public static final int START_ITEM_NUMBER = 3;
    public static final int MAX_HEALTH = 5;
    public static final int RELOAD_ITEM_NUMBER = 1;
    public static final int GUN_BULLET_SLOT_NUMBER = 6;
    static {
        rouletteItems.add(ModItems.MAGNIFYING_GLASS::getDefaultInstance);
        rouletteItems.add(ModItems.CHEWING::getDefaultInstance);
        rouletteItems.add(ModItems.CLIP::getDefaultInstance);
        rouletteItems.add(ModItems.STEEL_BALL::getDefaultInstance);
        rouletteItems.add(ModItems.REVERSING_CARD::getDefaultInstance);
        rouletteItems.add(ModItems.TELEPHONE::getDefaultInstance);
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
        /** 当前开火操作者UUID */
        public UUID operatorUUID = NONE_PLAYER.playerUUID;
    }
    public static class GamePlayerData {
        GamePlayerData(UUID playerUUID) {
            this.playerUUID = playerUUID;
        }
        public int getHealth() {
            return health;
        }
        public UUID getPlayerUUID() {
            return playerUUID;
        }
        public void addHealth(int health) {
            this.health += health;
            if(this.health > MAX_HEALTH)
                this.health = MAX_HEALTH;
            else if (this.health < 0)
                this.health = 0;
        }
        protected UUID playerUUID;
        protected int health = MAX_HEALTH;
    }
    public DevilRouletteGame(UUID player1ID, UUID player2ID, RandomSource random, Level level) {
        playerDataList = new ArrayList<>();
        playerDataList.add(new GamePlayerData(player1ID));
        playerDataList.add(new GamePlayerData(player2ID));
        currentPlayerData = playerDataList.getFirst();
        this.random = random;
        this.level = level;
    }
    public void init() {
        damage = 1;
        curListIdx = 0;
        bulletList.clear();
    }
    public boolean start() {
        List<Player> players = new ArrayList<>();
        for (var playerData : playerDataList) {
            if (playerData.getPlayerUUID() == null || level.getPlayerByUUID(playerData.getPlayerUUID()) == null)
            {
                // 出现空玩家返回false，在外部删除类
                return false;
            }
            players.add(level.getPlayerByUUID(playerData.getPlayerUUID()));
        }
        // 开局随机选择一个玩家启动
        currentPlayerData = playerDataList.get(random.nextInt(2));
        switch (gameMode) {
            case Roulette -> {
                break;
            }
            default -> {
                for (var player : players) {
                    // 游戏开始，清空游戏道具
                    clearRouletteItems(player);
                    for (int i = 0; i < START_ITEM_NUMBER; ++i) {
                        if (!rouletteItems.isEmpty()) {
                            player.addItem(getRandomItem());
                        }
                    }
                }
                break;
            }
        }
        reloadBullet();
        return true;
    }
    public static void clearRouletteItems(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ROULETTE_ITEMS.contains(stack.getItem())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
    public void reloadBullet() {
        bulletList.clear();
        List<Boolean> newBulletList = new ArrayList<>();
        // 添加实弹和虚弹
        // 整数计算：N * 2 / 3，向上取整
        int maxBullets = (GUN_BULLET_SLOT_NUMBER * 2 + 2) / 3;  // 等价于 ceil(N * 2/3)
        trueBulletNumber = random.nextInt(1, Math.max(maxBullets + 1, 2));
        for (int i = 0; i < GUN_BULLET_SLOT_NUMBER; ++i) {
            newBulletList.add(i < trueBulletNumber);
        }
        // 打乱实弹虚弹
        Collections.shuffle(newBulletList);
        bulletList.addAll(newBulletList);
        curListIdx = 0;
    }

    /**
     * 每轮发送道具
     * @return 如果有玩家不存在返回false
     */
    public boolean sendItemsAfterRound() {
        List<Player> players = new ArrayList<>();
        for (var playerData : playerDataList) {
            if (level.getPlayerByUUID(playerData.getPlayerUUID()) == null)
            {
                // 出现空玩家返回false，在外部删除类
                return false;
            }
            players.add(level.getPlayerByUUID(playerData.getPlayerUUID()));
        }
        // 每轮发放道具
        switch (gameMode) {
            case Roulette -> {
                break;
            }
            default -> {
                for (var player : players) {
                    for (int i = 0; i < RELOAD_ITEM_NUMBER; ++i)
                        player.addItem(getRandomItem());
                }
                break;
            }
        }
        return true;
    }
    public void sendItemToPlayer(Player player) {
        player.addItem(getRandomItem());
    }
    /**
     * 开火操作
     * @param target 操作目标
     * @return 弹丸结果
     */
    public FireResult fire(Target target) {
        FireResult result = new FireResult();
        result.operatorUUID = currentPlayerData.playerUUID;
        GamePlayerData targetPlayerData = playerDataList.get(indexOfResult(currentPlayerData.playerUUID, target));
        // 获取当前子弹，指针移向下一发子弹
        Boolean resultBullet = bulletList.get(curListIdx++);
        result.isTrueBullet = Boolean.TRUE.equals(resultBullet);
        if(Boolean.TRUE.equals(resultBullet)) {
            // 命中时减少damage伤害
            targetPlayerData.health -= damage;
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
        if (curListIdx >= bulletList.size()) {
            reloadBullet();
            if (!sendItemsAfterRound()) {
                // 如果发送物品失败则检查离线玩家，剩余玩家继续游戏
                List<UUID> players = getAlivePlayers();
                if (players.size() > 1) {
                    removeUnAlivePlayers();
                    // 如果游戏继续，则检查目标玩家是否存在，以及当前玩家是否存在，以确定操作权转换目标
                    if (!playerDataList.contains(targetPlayerData)) {
                        if (!playerDataList.contains(currentPlayerData)) {
                            // 如果二者都不存在则随机选择一个玩家
                            currentPlayerData = playerDataList.get(random.nextInt(playerDataList.size()));
                        }
                        else {
                            targetPlayerData = currentPlayerData;
                        }
                    }
                    // 重新发送道具
                    sendItemsAfterRound();
                }
                else {
                    // 人数不足结束游戏
                    if (players.size() == 1) {
                        // 如果只剩一人，则直接获胜
                        winner = getPlayerData(players.getFirst());
                    }
                    else {
                        // 没有玩家在线，则返回空获胜玩家
                        winner = NONE_PLAYER;
                    }
                    isGameEnd = true;
                }
            }
            result.isReload = true;
        }

        // 将操作权交给选择目标
        if (currentPlayerData != targetPlayerData) {
            currentPlayerData = targetPlayerData;
            result.isSwitch = true;
        }

        // 重置伤害
        damage = 1;
        return result;
    }
    public FireResult forceGameOverByKillPlayer(UUID playerID) {
        FireResult result = new FireResult();
        result.isTrueBullet = true;
        result.isTargetAlive = false;
        result.operatorUUID = playerID;
        for (GamePlayerData playerData : playerDataList)
            if (playerData.getPlayerUUID() == playerID) {
                playerData.health = 0;
                winner = playerData;
            }
        isGameEnd = true;
        return result;
    }

    /**
     * 校验玩家是否存在，如果不存在则结束有歘，返回不存在玩家的索引
     * @return -1 表示所有玩家都存在
     */
    public int checkUnAlivePlayer() {
        for (int i = 0; i < playerDataList.size(); ++i) {
            if (level.getPlayerByUUID(playerDataList.get(i).playerUUID) == null)
                return i;
        }
        return -1;
    }
    /** 获取存在玩家，没有玩家存在则返回空列表 */
    public List<UUID> getAlivePlayers() {
        List<UUID> alivePlayer = new ArrayList<>();
        for (GamePlayerData gamePlayerData : playerDataList) {
            if (level.getPlayerByUUID(gamePlayerData.playerUUID) != null)
                alivePlayer.add(gamePlayerData.playerUUID);
        }
        return alivePlayer;
    }
    public void removeUnAlivePlayers() {
        playerDataList.removeIf(gamePlayerData -> level.getPlayerByUUID(gamePlayerData.playerUUID) == null);
    }

    public boolean canOperate(UUID playerID) {
        return playerID == currentPlayerData.playerUUID;
    }

    public int indexOfResult(UUID playerID, Target target) {
        if (playerDataList.getFirst().playerUUID ==  playerID) {
            // 如果操作玩家是玩家1，且目标为自己，则返回索引0
            return target == Target.self ? 0 : 1;
        }
        // 如果操作玩家是玩家2，且目标为自己，则返回索引1
        return target == Target.self ? 1 : 0;
    }

    public boolean isGameEnd() {
        return isGameEnd;
    }
    public int getHealth(UUID playerID) {
        for (GamePlayerData playerData : playerDataList)
            if (playerData.playerUUID == playerID)
                return playerData.health;
        return 0;
    }
    public GamePlayerData getPlayerData(UUID playerID) {
        for (GamePlayerData playerData : playerDataList)
            if (playerData.playerUUID == playerID)
                return playerData;
        return NONE_PLAYER;
    }

    public ItemStack getRandomItem() {
        return rouletteItems.get(random.nextInt(rouletteItems.size())).get();
    }
    public int getTrueBulletNumber() {
        return trueBulletNumber;
    }
    public List<Boolean> getBulletList() {
        return bulletList;
    }
    public GamePlayerData getWinner() {
        return winner;
    }
    public GamePlayerData getCurrentPlayerData() {
        return currentPlayerData;
    }
    public int getCurListIdx() {
        return curListIdx;
    }
    public boolean getCurBullet() {
        return bulletList.get(curListIdx);
    }
    public void setCurBullet(boolean isTrueBullet){
        bulletList.set(curListIdx, isTrueBullet);
    }
    public void reverseCurBullet() {
        bulletList.set(curListIdx, !bulletList.get(curListIdx));
    }
    public int getDamage() {
        return damage;
    }
    public void setDamage(int damage) {
        this.damage = damage;
    }
    public void addDamage(int damage) {
        this.damage += damage;
    }
    /** 获取随机一个剩余实弹的索引, 没有则返回-1 */
    public int getRandomTrueBulletIdx() {
        List<Integer> lastTrueBulletIdxList = new ArrayList<>();
        for (int i = curListIdx; i < bulletList.size(); ++i) {
            if (bulletList.get(i)) {
                lastTrueBulletIdxList.add(i);
            }
        }
        if (lastTrueBulletIdxList.isEmpty())
            return -1;
        return lastTrueBulletIdxList.get(random.nextInt(lastTrueBulletIdxList.size()));
    }

    public void setRandom(RandomSource random) {
        this.random = random;
    }
    public void setLevel(Level level) {
        this.level = level;
    }
    public Level getLevel() {
        return level;
    }
    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    protected List<GamePlayerData> playerDataList;
    /** 弹丸列表 */
    protected List<Boolean> bulletList = new ArrayList<>();
    protected Level level;
    protected RandomSource random;
    /** 当前操作玩家 */
    protected GamePlayerData currentPlayerData;
    protected GamePlayerData winner = null;
    protected GameMode gameMode = GameMode.Lobby;
    protected boolean isGameEnd = false;
    protected int trueBulletNumber;
    protected int curListIdx = 0;
    protected int damage = 1;
}
