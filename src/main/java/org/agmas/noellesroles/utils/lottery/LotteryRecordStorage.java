package org.agmas.noellesroles.utils.lottery;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.agmas.noellesroles.Noellesroles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 抽奖数据存储器
 * - 负责读写抽奖数据
 * - 目前只有皮肤抽奖，如果有其他类型的抽奖也可以利用此类完成：仅需修改LOTTERY_DATA_DIR_NAME并继承此类即可，具体操作：
 *      newStorage.java: @overrive 构造函数，修改LOTTERY_DATA_DIR_NAME
 *      newRecord.java: @overrive 构造函数、toNbt和fromNbt方法
 *      TODO : 抽象化这两个类使其更方便自适应各种抽奖类型
 *      TODO : 更近一步把他们变成更加通用的记录类组合用于记录任何数据
 */
public class LotteryRecordStorage {
    protected LotteryRecordStorage() {
        // 使用 FabricLoader获取服务器目录
        // TODO :  - fix(LootRecord) : 修复了根目录地址为服务器根目录（没在带子服的服务器实验不确定是否生效）
        // TODO : 似乎在java中直接查找(无法确定子服和主服的位置关系)不是很稳妥的方式，请使用配置文件指定路径
        this.lotteryDataDir = FabricLoader.getInstance()
                .getGameDir()  // 获取服务器当前目录
                .resolve(LOTTERY_DATA_DIR_NAME);
        this.playerDataDir = lotteryDataDir.resolve(PLAYER_DATA_DIR_NAME);

        try {
            Files.createDirectories(playerDataDir);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("Failed to create player data directory.\n{}", String.valueOf(e));
        }
    }
    public static LotteryRecordStorage getInstance() {
        return instance;
    }

    /** 保存玩家数据*/
    public void savePlayerData(UUID playerId) {
        LotteryRecordData lotteryRecordData = lotteryRecordsCache.get(playerId);
        if (lotteryRecordData == null) {
            return;
        }

        try {
            // 使用临时文件，避免写入过程中崩溃
            Path playerFilePath = getPlayerDataFilePath(playerId);
            Path tempFile = playerFilePath.resolveSibling(playerFilePath.getFileName() + ".tmp");

            // 存储为 nbt数据
            CompoundTag tag = lotteryRecordData.toNbt();
            NbtIo.write(tag, tempFile);

            // 原子性替换 tmp-> dat
            Files.move(tempFile, playerFilePath, StandardCopyOption.REPLACE_EXISTING);

            dirtyPlayers.remove(playerId);

            Noellesroles.LOGGER.info("Saved player data for player {}", playerId);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("Failed to save player data for player {}\n{}", playerId, String.valueOf(e));
        }
    }
    /** 保存所有脏数据*/
    public void saveAllDirtyPlayersData() {
        if(dirtyPlayers.isEmpty())
            return;
        Noellesroles.LOGGER.info("Start saving all dirty player data. Num:{}",dirtyPlayers.size());
        int completeNum = 0;
        int failNum = 0;
        for (UUID playerId : dirtyPlayers) {
            try{
                savePlayerData(playerId);
                ++completeNum;
            }
            catch (Exception e){
                ++failNum;
                Noellesroles.LOGGER.error("Failed to save player data for player {}\n{}", playerId, String.valueOf(e));
            }
        }
        Noellesroles.LOGGER.info("Saved all dirty player data. Complete:{}, Fail:{}",completeNum,failNum);
    }
    protected LotteryRecordData loadLotteryDataFromFile(UUID playerUuid) {
        Path file = getPlayerDataFilePath(playerUuid);
        if(!Files.exists(file))
            return null;
        try {
            CompoundTag tag = NbtIo.read(file);
            if(tag == null){
                Noellesroles.LOGGER.warn("Player data file is missing. Player:{}",playerUuid);
                return null;
            }
            return LotteryRecordData.fromNbt(tag);
        } catch (IOException e) {
            Noellesroles.LOGGER.error("Failed to load player data for player {}\n{}", playerUuid, String.valueOf(e));
            return null;
        }
    }
    protected LotteryRecordData createNewLotteryRecordData(UUID playerUuid){
        return new LotteryRecordData(0, playerUuid, new ArrayList<>());
    }
    /** 更新玩家数据并标记为脏*/
    public void updatePlayerLotteryData(UUID playerId, Consumer<LotteryRecordData> lotteryRecordDataConsumer) {
        LotteryRecordData lotteryRecordData = getPlayerLotteryRecord(playerId);
        lotteryRecordDataConsumer.accept(lotteryRecordData);
        markPlayerDirty(playerId);
    }
    /** 获取玩家抽奖数据*/
    public LotteryRecordData getPlayerLotteryRecord(UUID playerUuid){
        if(lotteryRecordsCache.containsKey(playerUuid))
            return lotteryRecordsCache.get(playerUuid);
        LotteryRecordData record = loadLotteryDataFromFile(playerUuid);
        // 当无该玩家数据时自动创建
        if(record == null) {
            record = createNewLotteryRecordData(playerUuid);
            markPlayerDirty(playerUuid);
        }
        // 将目标数据存入缓存
        lotteryRecordsCache.put(playerUuid, record);
        return record;
    }
    protected Path getPlayerDataFilePath(UUID playerUuid){
        return playerDataDir.resolve(playerUuid.toString() + ".dat");
    }
    public void markPlayerDirty(UUID playerId){
        dirtyPlayers.add(playerId);
    }
    public Path getLotteryDataDir() {
        return lotteryDataDir;
    }

    protected static final LotteryRecordStorage instance = new LotteryRecordStorage();
    protected static final String LOTTERY_DATA_DIR_NAME = "lottery_skin_data";
    protected static final String PLAYER_DATA_DIR_NAME = "players";
    /** 存储记录缓存*/
    protected final Map<UUID, LotteryRecordData> lotteryRecordsCache = new ConcurrentHashMap<>();
    /** 存储脏玩家数据：统一写入*/
    protected final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    protected final Path lotteryDataDir;
    protected final Path playerDataDir;
}
