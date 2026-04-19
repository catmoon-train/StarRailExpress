package org.agmas.noellesroles.mini_gme;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 *      - 钢珠：下一发如果为实弹则造成2点伤害
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
    public static final int START_ITEM_NUMBER = 3;
    public static final int START_HEALTH = 5;
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
        public void addHealth(int health) {
            this.health += health;
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
        damage = 1;
        curListIdx = 0;
        bulletList.clear();
    }
    public void start() {
        // 开局随机选择一个玩家启动
        currentPlayerData = playerDataList.get(random.nextInt(2));
        switch (gameMode) {
            case Roulette -> {
                break;
            }
            default -> {
                for (var playerData : playerDataList) {
                    // 游戏开始，清空游戏道具
                    clearRouletteItems(playerData.player);
                    for (int i = 0; i < START_ITEM_NUMBER; ++i) {
                        if (!rouletteItems.isEmpty()) {
                            playerData.player.addItem(getRandomItem());
                        }
                    }
                }
                break;
            }
        }
        reloadBullet();
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
        // 每轮发放道具
        switch (gameMode) {
            case Roulette -> {
                break;
            }
            default -> {
                for (var playerData : playerDataList) {
                    for (int i = 0; i < RELOAD_ITEM_NUMBER; ++i)
                        playerData.player.addItem(getRandomItem());
                }
                break;
            }
        }

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
     * 开火操作
     * @param target 操作目标
     * @return 弹丸结果
     */
    public FireResult fire(Target target) {
        FireResult result = new FireResult();
        GamePlayerData targetPlayerData = playerDataList.get(indexOfResult(currentPlayerData.player, target));
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

    public boolean canOperate(Player player) {
        return player == currentPlayerData.player;
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

    protected List<GamePlayerData> playerDataList;
    /** 弹丸列表 */
    protected List<Boolean> bulletList = new ArrayList<>();
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
