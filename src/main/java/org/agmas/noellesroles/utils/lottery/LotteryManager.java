package org.agmas.noellesroles.utils.lottery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.Pair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 抽奖管理器
 * - 实现抽奖功能
 * - 管理抽奖数据的调用
 */
public class LotteryManager {
    public static class LotteryPool {
        public LotteryPool(int poolID, String name, String type,
                List<LotteryPoolsConfig.PoolConfig.QualityListItemConfig> qualityListGroupConfigs) {
            this.poolID = poolID;
            this.name = name;
            this.type = type;
            this.qualityListGroupConfigs = new ArrayList<>();
            for (LotteryPoolsConfig.PoolConfig.QualityListItemConfig qualityListItemConfig : qualityListGroupConfigs) {
                this.qualityListGroupConfigs.add(
                        new Pair<>(qualityListItemConfig.getProbability(), qualityListItemConfig.getItemList()));
            }
        }

        /** 使用已有卡池类构造，为了防止形参冲突，与读取配置的构造相区别，可以接受不同的列表 */
        public LotteryPool(String name, int poolID, String type,
                List<Pair<Double, List<String>>> qualityListGroupConfigs) {
            this.poolID = poolID;
            this.name = name;
            this.type = type;
            this.qualityListGroupConfigs = qualityListGroupConfigs;
        }

        /**
         * 抽奖一次
         *
         * @param player 基于玩家进行随机:抽奖结果需要对玩家信息查询修改，因此必然有player参数传入，所以直接用玩家源
         * @return 返回抽奖结果：结果的品质和在该品质内的索引
         */
        public Pair<Integer, Integer> rollOnce(ServerPlayer player) {
            RandomSource randomSource = player.getRandom();
            int curNum = randomSource.nextInt(maxGranularity);// 0 ~ maxGranularity -1
            double level = 0.0f;
            for (int i = 0; i < qualityListGroupConfigs.size(); ++i) {
                level += qualityListGroupConfigs.get(i).first;
                if (curNum < level * maxGranularity) {
                    List<String> curQualityList = qualityListGroupConfigs.get(i).second;
                    // TODO : 为玩家解锁皮肤
                    // TODO : 处理重复皮肤
                    int resultIdx = randomSource.nextInt(curQualityList.size());
                    int resultQuality = i;
                    LotteryRecordStorage.getInstance().updatePlayerLotteryData(player.getUUID(),
                            lotteryRecordData -> lotteryRecordData.lotteryItems.add(
                                    new LotteryRecordData.LotteryItemData(
                                            this.poolID, resultQuality, curQualityList.get(resultIdx),
                                            System.currentTimeMillis())));
                    return new Pair<>(resultQuality, resultIdx);
                }
            }
            Noellesroles.LOGGER.warn("[LootSys] 玩家UUID:" + player.getUUID() + "抽奖失败");
            return new Pair<>(-1, -1);
        }

        public int getPoolID() {
            return poolID;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public List<Pair<Double, List<String>>> getQualityListGroupConfigs() {
            return qualityListGroupConfigs;
        }

        private final int poolID;
        private final String name;
        private final String type;

        /**
         * 各品质卡池内容及其概率
         * <p>
         * - 品质为由低到高，概率为从高到低
         * </p>
         */
        private final List<Pair<Double, List<String>>> qualityListGroupConfigs;
    }

    public ResourceLocation getQualityBgResourceLocation(int index) {
        if (index < 0)
            return qualityBgList[0];
        else if (index >= qualityBgList.length)
            return qualityBgList[qualityBgList.length - 1];
        return qualityBgList[index];
    }

    /** 检查玩家的抽奖次数 > 0 */
    public boolean canRoll(ServerPlayer player) {
        if (player == null)
            return false;

        LotteryRecordData lotteryRecordData = LotteryRecordStorage.getInstance()
                .getPlayerLotteryRecord(player.getUUID());
        return lotteryRecordData.lotteryChance > 0;
    }

    /** 添加抽奖机会 */
    public void addOrDegreeLotteryChance(ServerPlayer player, int chance) {
        LotteryRecordStorage.getInstance().updatePlayerLotteryData(player.getUUID(),
                lotteryRecordData -> lotteryRecordData.lotteryChance += chance);
        LotteryRecordStorage.getInstance().savePlayerData(player.getUUID());
    }

    /**
     * 添加卡池
     * <p>
     * NOTE:
     * 该函数用于客户端初次同步服务端卡池，而不是用来运行时添加卡池的；具体而言：
     * 指客户端首次打开抽奖界面会发送完整卡池信息，客户端保存缓存（仅内存），下次访问卡池信息时会从缓存中读取而不用再发送卡池信息
     * TODO:未来需要将卡池信息缓存为本地文件，每次启动自动读取，调用时与服务器卡池信息文件hash值进行对比同步，进一步减少发包
     * </p>
     */
    public void addLotteryPool(LotteryPool lotteryPool) {
        lotteryPoolList.add(lotteryPool);
    }

    public List<Integer> getPoolIDs() {
        List<Integer> poolIDs = new ArrayList<>();
        for (LotteryPool lotteryPool : lotteryPoolList) {
            poolIDs.add(lotteryPool.getPoolID());
        }
        return poolIDs;
    }

    /** 获取指定 ID的卡池 */
    public LotteryPool getLotteryPool(int poolID) {
        for (LotteryPool lotteryPool : lotteryPoolList) {
            if (lotteryPool.getPoolID() == poolID) {
                return lotteryPool;
            }
        }
        return null;
    }

    public List<LotteryPool> getLotteryPools() {
        return lotteryPoolList;
    }

    public void setLotteryPoolByID(int poolID, LotteryPool lotteryPool) {
        for (int i = 0; i < lotteryPoolList.size(); ++i) {
            if (lotteryPoolList.get(i).poolID == poolID) {
                lotteryPoolList.set(i, lotteryPool);
                return;
            }
        }
    }

    public void sortPools() {
        lotteryPoolList.sort(Comparator.comparingInt(LotteryPool::getPoolID));
    }

    // TODO :
    // 会优先读取本地数据，理论上没有人有本地配置因此不影响向服务器请求，但是如果本地真有配置，就会顶掉对应id的卡池，甚至有服务器不存在的卡池会导致未知情况
    private void init() {
        // 为了让单人测试也生效，暂时让客户端也能读取配置，并且未来可以加入本地数据存储，进行卡池信息hash值对比更新，也能减少数据传输量
        // // 仅运行在服务端
        // if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
        // {
        // return;
        // }
        // 配置文件应该存放在主目录lottery_data目录下的lottery_pool.json文件中
        Path configPath = LotteryRecordStorage.getInstance().getLotteryDataDir().resolve("lottery_pool.json");
        LotteryPoolsConfig lotteryPoolsConfig = LotteryPoolsConfigParser.parse(configPath);
        if (lotteryPoolsConfig == null || lotteryPoolsConfig.getPools().isEmpty()) {
            Noellesroles.LOGGER.error("[LootSys] No valid pool configuration found.Path:{}", configPath);
            return;
        }
        for (LotteryPoolsConfig.PoolConfig poolConfig : lotteryPoolsConfig.getPools()) {
            // 验证配置数据
            try {
                if (!poolConfig.isEnable())
                    continue;
                if (poolConfig.getPoolName() == null || poolConfig.getPoolName().isEmpty()) {
                    Noellesroles.LOGGER.error("[LootSys] Pool name is null or empty.");
                    continue;
                }
                if (poolConfig.getPoolType() == null || poolConfig.getPoolType().isEmpty()) {
                    Noellesroles.LOGGER.error("[LootSys] Pool type is null or empty.");
                    continue;
                }
                Double sumProbability = 0.0;
                for (LotteryPoolsConfig.PoolConfig.QualityListItemConfig qualityListItemConfig : poolConfig
                        .getQualityListGroup()) {
                    if (qualityListItemConfig.getItemList() == null || qualityListItemConfig.getItemList().isEmpty()) {
                        Noellesroles.LOGGER.error("[LootSys] Quality list is null or empty.");
                        continue;
                    }
                    if (qualityListItemConfig.getProbability() == null || qualityListItemConfig.getProbability() <= 0) {
                        Noellesroles.LOGGER.error("[LootSys] Quality list probability is null or <= 0.");
                        continue;
                    }
                    sumProbability += qualityListItemConfig.getProbability();
                }
                if (sumProbability < 0.999 || sumProbability > 1.001) {
                    Noellesroles.LOGGER.error("[LootSys] Quality list probability sum({}) is not equal to 1.",
                            sumProbability);
                    continue;
                }
                LotteryPool pool = new LotteryPool(
                        poolConfig.getPoolID(), poolConfig.getPoolName(), poolConfig.getPoolType(),
                        poolConfig.getQualityListGroup());
                // 将卡池添加进列表
                this.lotteryPoolList.add(pool);
                Noellesroles.LOGGER.info("[LootSys] Loaded pool:ID:{},Name{},Type{}", pool.poolID, pool.name,
                        pool.type);
            } catch (Exception e) {
                Noellesroles.LOGGER.error("[LootSys] Failed to parse pool config : {}", poolConfig.getPoolName(), e);
            }
        }
        Noellesroles.LOGGER.info("[LootSys] Loaded {} pools.", lotteryPoolList.size());
    }

    private LotteryManager() {
        init();
    };

    public static LotteryManager getInstance() {
        if (instance == null)
            instance = new LotteryManager();
        return instance;
    }

    private static LotteryManager instance = null;

    /**
     * 抽奖粒度
     * <p>
     * - 抽奖粒度越小，抽奖概率越准确
     * - 具体实现：抽取时生成0~该值的随机数，由pollProbability数组的百分比进行按顺序切分，直到找到对应的概率区间
     * </p>
     */
    public static final int maxGranularity = 1000;
    private final ArrayList<LotteryPool> lotteryPoolList = new ArrayList<>();
    private final String defaultPoolItem = "coin_common";
    private static final ResourceLocation[] qualityBgList = {
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/common_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/uncommon_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/rare_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/epic_skin.png"),
            ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/gui/loot/legendary_skin.png"),
    };
}
