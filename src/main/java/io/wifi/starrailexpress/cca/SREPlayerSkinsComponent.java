package io.wifi.starrailexpress.cca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.item.SkinableItem;
import io.wifi.starrailexpress.sync.MysqlPlayerDataStore;
import io.wifi.starrailexpress.util.SkinManager;
import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SREPlayerSkinsComponent implements AutoSyncedComponent, ServerTickingComponent {
    private static final Logger logger = LoggerFactory.getLogger(SREPlayerSkinsComponent.class);
    private static final String DATABASE_SYNC_KEY = "skins";
    private static final long DATABASE_SYNC_DEBOUNCE_MS = 2500L;
    private static final long DATABASE_SYNC_FLUSH_TIMEOUT_MS = 4000L;
    public static final ComponentKey<SREPlayerSkinsComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("player_skins"),
            SREPlayerSkinsComponent.class);
    public static final ResourceLocation WEAPON_SKINS_DATA_ID = SRE.id("weapon_skins");

    private static final Gson GSON = new GsonBuilder().create();

    private final Player player;
    private Map<String, String> equippedSkins; // 存储当前装备的皮肤 {itemName -> skinName}
    private Map<String, Map<String, Boolean>> unlockedSkins; // 存储解锁的皮肤 {itemName -> {skinName -> isUnlocked}}
    private Integer lootChance;
    private Integer coinNum;
    private boolean isNetworkSyncEnabled = false;
    private boolean syncMode = false;
    private volatile boolean databaseSyncQueued = false;
    private volatile boolean databaseSyncInFlight = false;
    private volatile long nextDatabaseSyncAt = 0L;

    public SREPlayerSkinsComponent(Player player) {
        this.player = player;
        this.equippedSkins = new HashMap<>();
        this.unlockedSkins = new HashMap<>();
        this.lootChance = 0;
        this.coinNum = 0;
    }

    public void sync() {
        this.syncMode = true;
        KEY.sync(this.player);
        this.syncMode = false;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        if (!SREConfig.instance().isItemSkinEnabled) {
            return false;
        }
        return this.player == serverPlayer;
    }

    /**
     * 初始化网络同步
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     */
    public void initializeNetworkSync(String host, int port, String key) {
        this.isNetworkSyncEnabled = SREConfig.instance().itemSkinSyncServerEnabled
                && SREConfig.instance().mysqlPlayerSyncEnabled
                && MysqlPlayerDataStore.isAvailable();
        if (this.isNetworkSyncEnabled) {
            logger.info("玩家 {} 的皮肤 MySQL 同步已启用", this.player.getName().getString());
        } else if (SREConfig.instance().itemSkinSyncServerEnabled) {
            logger.warn("玩家 {} 的皮肤 MySQL 同步未启用，数据库不可用或配置未完成。", this.player.getName().getString());
        }
    }

    /**
     * 禁用全局网络同步
     */
    public static void disableGlobalNetworkSync() {
        MysqlPlayerDataStore.shutdown();
    }

    /**
     * 禁用网络同步
     */
    public void disableNetworkSync() {
        this.isNetworkSyncEnabled = false;
    }

    @Override
    public void serverTick() {
        if (!this.isNetworkSyncEnabled || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.serverLevel().getGameTime() % 20L != 0L) {
            return;
        }
        if (this.databaseSyncQueued && System.currentTimeMillis() >= this.nextDatabaseSyncAt) {
            flushSkinDataToDatabase(false);
        }
    }

    /** 获取当前玩家抽奖次数 */
    public Integer getLootChance() {
        return this.lootChance;
    }

    /** 获取当前玩家金币数量 */
    public Integer getCoinNum() {
        return this.coinNum;
    }

    public void addLootChance(Integer num) {
        this.lootChance += num;
        // 触发网络同步
        markSkinDataChanged();
    }

    public void addCoinNum(Integer num) {
        this.coinNum += num;
        // 触发网络同步
        markSkinDataChanged();
    }

    /**
     * 获取当前装备的皮肤名称
     */
    public String getEquippedSkin(String itemName) {
        return equippedSkins.getOrDefault(normalizeItemName(itemName), "default");
    }

    /**
     * 获取当前装备的皮肤名称
     */
    public String getEquippedSkin(ItemStack itemStack) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        return equippedSkins.getOrDefault(itemName, "default");
    }

    /**
     * 设置当前装备的皮肤名称
     */
    public void setEquippedSkin(ItemStack itemStack, String skinName) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        equippedSkins.put(itemName, skinName);
        markSkinDataChanged();
    }

    /**
     * 解锁一个皮肤
     */
    public void unlockSkin(ItemStack itemStack, String skinName) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        unlockedSkins.computeIfAbsent(itemName, k -> new HashMap<>()).put(skinName, true);
        // 触发网络同步
        markSkinDataChanged();
    }

    /**
     * 解锁指定物品类型的皮肤
     */
    public void unlockSkinForItemType(String itemTypeName, String skinName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        unlockedSkins.computeIfAbsent(normalizedItemName, k -> new HashMap<>()).put(skinName, true);
        // 触发网络同步
        markSkinDataChanged();
    }

    /**
     * 锁定一个皮肤（移除解锁状态）
     */
    public void lockSkin(ItemStack itemStack, String skinName) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        Map<String, Boolean> skinsForItem = unlockedSkins.get(itemName);
        if (skinsForItem != null) {
            skinsForItem.remove(skinName);
            // 如果物品没有其他解锁的皮肤，移除该物品的条目
            if (skinsForItem.isEmpty()) {
                unlockedSkins.remove(itemName);
            }
        }
        // 触发网络同步
        markSkinDataChanged();
    }

    /**
     * 锁定指定物品类型的皮肤
     */
    public void clearSkinForItemType(String itemTypeName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        unlockedSkins.remove(normalizedItemName);
        markSkinDataChanged();
    }

    /**
     * 锁定指定物品类型的皮肤
     */
    public void lockSkinForItemType(String itemTypeName, String skinName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        Map<String, Boolean> skinsForItem = unlockedSkins.get(normalizedItemName);
        if (skinsForItem != null) {
            skinsForItem.remove(skinName);
            // 如果物品没有其他解锁的皮肤，移除该物品的条目
            if (skinsForItem.isEmpty()) {
                unlockedSkins.remove(normalizedItemName);
            }
        }
        markSkinDataChanged();
    }

    /**
     * 检查皮肤是否已解锁
     */
    public boolean isSkinUnlocked(ItemStack itemStack, String skinName) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        Map<String, Boolean> skinsForItem = unlockedSkins.get(itemName);
        return skinsForItem != null && skinsForItem.getOrDefault(skinName, false);
    }

    /**
     * 检查指定物品类型的皮肤是否已解锁
     */
    public boolean isSkinUnlockedForItemType(String itemTypeName, String skinName) {
        if (Objects.equals(skinName, "default"))
            return true;
        String normalizedItemName = normalizeItemName(itemTypeName);
        Map<String, Boolean> skinsForItem = unlockedSkins.get(normalizedItemName);
        return skinsForItem != null && skinsForItem.getOrDefault(skinName, false);
    }

    /**
     * 获取所有解锁的皮肤
     */
    public Map<String, Boolean> getUnlockedSkins(ItemStack itemStack) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toLowerCase();
        return unlockedSkins.getOrDefault(itemName, new HashMap<>());
    }

    /**
     * 获取指定物品类型的所有解锁皮肤
     */
    public Map<String, Boolean> getUnlockedSkinsForItemType(String itemTypeName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        return unlockedSkins.getOrDefault(normalizedItemName, new HashMap<>());
    }

    /**
     * 设置指定物品类型的装备皮肤
     */
    public void setEquippedSkinForItemType(String itemTypeName, String skinName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        equippedSkins.put(normalizedItemName, skinName);
        markSkinDataChanged();
    }

    /**
     * 获取所有装备的皮肤映射
     */
    public Map<String, String> getEquippedSkins() {
        return new HashMap<>(this.equippedSkins);
    }

    /**
     * 获取所有解锁的皮肤映射
     */
    public Map<String, Map<String, Boolean>> getUnlockedSkins() {
        return new HashMap<>(this.unlockedSkins);
    }

    /**
     * 同步皮肤数据到客户端
     */
    public void syncSkinsToClient() {
        sync();
    }

    /**
     * 从数据同步令牌获取皮肤数据
     */
    public String getSkinFromDataSync(ItemStack itemStack) {
        String itemName = "default";
        if (itemStack.getItem() instanceof SkinableItem ski) {
            itemName = ski.getItemSkinType();
        } else {
            itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath();
        }
        // 使用物品的注册名而不是显示名称，以确保一致性

        if (KEY.get(player).equippedSkins.containsKey(itemName)) {
            return KEY.get(player).equippedSkins.get(itemName);
        }

        return "default";
    }

    /**
     * 设置数据同步中的皮肤
     */
    public void setSkinInDataSync(ItemStack itemStack, String skinName) {
        // 只在客户端上传数据
        KEY.get(player).equippedSkins.put(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath(), skinName);
        markSkinDataChanged();
    }

    /**
     * 标准化物品名称
     */
    public static String normalizeItemName(String itemTypeName) {
        // 将物品类型名称标准化为小写，去除空格等
        var itr = ResourceLocation.tryParse(itemTypeName);
        if (itr == null)
            return itemTypeName;
        return itr.getPath();
    }

    /**
     * 标记皮肤数据已改变，需要网络同步
     */
    private void markSkinDataChanged() {
        this.sync();
        if (this.isNetworkSyncEnabled) {
            this.databaseSyncQueued = true;
            this.nextDatabaseSyncAt = System.currentTimeMillis() + DATABASE_SYNC_DEBOUNCE_MS;
        }
    }

    /**
     * 将皮肤数据异步同步到 MySQL
     */
    public void syncSkinsToNetwork() {
        this.databaseSyncQueued = true;
        this.nextDatabaseSyncAt = 0L;
        flushSkinDataToDatabase(false);
    }

    /**
     * 从 MySQL 异步拉取皮肤数据
     */
    public void pullSkinsFromNetwork() {
        if (!SREConfig.instance().itemSkinSyncServerEnabled)
            return;
        if (!this.isNetworkSyncEnabled || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (serverPlayer.getServer() == null) {
            return;
        }

        MysqlPlayerDataStore.loadBatchAsync(this.player.getUUID(), List.of(DATABASE_SYNC_KEY))
                .thenAccept(records -> {
                    MysqlPlayerDataStore.SyncRecord record = records.get(DATABASE_SYNC_KEY);
                    if (record == null || record.payload() == null || record.payload().isBlank()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> skinData = GSON.fromJson(record.payload(), Map.class);
                    if (skinData == null) {
                        return;
                    }
                    serverPlayer.getServer().execute(() -> {
                        this.applyNetworkSkinData(skinData);
                        this.sync();
                        logger.debug("玩家 {} 的皮肤数据已从 MySQL 拉取", this.player.getName().getString());
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("从 MySQL 拉取玩家 {} 的皮肤数据时出错", this.player.getName().getString(), throwable);
                    return null;
                });
    }

    /**
     * 应用从网络获取的皮肤数据
     */
    @SuppressWarnings("unchecked")
    private void applyNetworkSkinData(Map<String, Object> skinData) {
        try {
            if (skinData.containsKey("equipped")) {
                Object equipped = skinData.get("equipped");
                if (equipped instanceof Map) {
                    this.equippedSkins = new HashMap<>((Map<String, String>) equipped);
                }
            }

            if (skinData.containsKey("unlocked")) {
                Object unlocked = skinData.get("unlocked");
                if (unlocked instanceof Map) {
                    Map<String, Map<String, Boolean>> unlockedData = (Map<String, Map<String, Boolean>>) unlocked;
                    this.unlockedSkins = this.deepCopyMap(unlockedData);
                }
            }

            if (skinData.containsKey("lootChance")) {
                Object lootChance = skinData.get("lootChance");
                if (lootChance instanceof Number nb) {
                    this.lootChance = nb.intValue();
                }
            }

            if (skinData.containsKey("coinNum")) {
                Object coinNum = skinData.get("coinNum");
                if (coinNum instanceof Number nb) {
                    this.coinNum = nb.intValue();
                }
            }

        } catch (Exception e) {
            logger.error("应用网络皮肤数据时出错", e);
        }
    }

    public boolean flushNetworkSyncBlocking() {
        return flushSkinDataToDatabase(true);
    }

    /**
     * 深复制解锁皮肤映射
     */
    private Map<String, Map<String, Boolean>> deepCopyUnlockedSkins() {
        Map<String, Map<String, Boolean>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Boolean>> entry : this.unlockedSkins.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * 深复制嵌套的映射
     */
    private Map<String, Map<String, Boolean>> deepCopyMap(Map<String, Map<String, Boolean>> original) {
        Map<String, Map<String, Boolean>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Boolean>> entry : original.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * 检查网络同步是否已启用
     */
    public boolean isNetworkSyncEnabled() {
        return this.isNetworkSyncEnabled;
    }

    private boolean flushSkinDataToDatabase(boolean blocking) {
        if (!this.isNetworkSyncEnabled) {
            return false;
        }

        String payloadJson = GSON.toJson(buildSkinDataPayload());
        Map<String, String> payloads = Map.of(DATABASE_SYNC_KEY, payloadJson);
        long updatedAt = System.currentTimeMillis();

        if (blocking) {
            this.databaseSyncQueued = false;
            boolean success = MysqlPlayerDataStore.saveBatchBlocking(
                    this.player.getUUID(),
                    payloads,
                    updatedAt,
                    DATABASE_SYNC_FLUSH_TIMEOUT_MS);
            if (!success) {
                logger.warn("阻塞刷新玩家 {} 的皮肤 MySQL 数据失败。", this.player.getName().getString());
            }
            return success;
        }

        if (this.databaseSyncInFlight) {
            return false;
        }

        this.databaseSyncQueued = false;
        this.databaseSyncInFlight = true;
        MysqlPlayerDataStore.saveBatchAsync(this.player.getUUID(), payloads, updatedAt)
                .whenComplete((success, throwable) -> {
                    this.databaseSyncInFlight = false;
                    if (throwable != null) {
                        logger.warn("异步刷新玩家 {} 的皮肤 MySQL 数据失败。", this.player.getName().getString(), throwable);
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        logger.warn("异步刷新玩家 {} 的皮肤 MySQL 数据未成功写入。", this.player.getName().getString());
                    }
                });
        return true;
    }

    private Map<String, Object> buildSkinDataPayload() {
        Map<String, Object> skinData = new HashMap<>();
        skinData.put("equipped", new HashMap<>(this.equippedSkins));
        skinData.put("unlocked", this.deepCopyUnlockedSkins());
        skinData.put("lootChance", this.lootChance);
        skinData.put("coinNum", this.coinNum);
        skinData.put("version", System.currentTimeMillis());
        skinData.put("timestamp", System.currentTimeMillis());
        return skinData;
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        // 读取装备的皮肤数据
        if (compoundTag.contains("equippedSkins")) {
            CompoundTag equippedSkinsTag = compoundTag.getCompound("equippedSkins");
            this.equippedSkins.clear();
            for (String key : equippedSkinsTag.getAllKeys()) {
                this.equippedSkins.put(key, equippedSkinsTag.getString(key));
            }
        }
        if (compoundTag.contains("lootChance")) {
            this.lootChance = compoundTag.getInt("lootChance");
        } else {
            this.lootChance = 0;
        }
        if (compoundTag.contains("coinNum")) {
            this.coinNum = compoundTag.getInt("coinNum");
        } else {
            this.coinNum = 0;
        }
        if (compoundTag.contains("unlockedSkins")) {
            // 读取解锁的皮肤数据
            CompoundTag unlockedSkinsTag = compoundTag.getCompound("unlockedSkins");
            this.unlockedSkins.clear();
            for (String itemKey : unlockedSkinsTag.getAllKeys()) {
                CompoundTag skinsForItemTag = unlockedSkinsTag.getCompound(itemKey);
                Map<String, Boolean> skinsForItem = new HashMap<>();
                for (String skinKey : skinsForItemTag.getAllKeys()) {
                    skinsForItem.put(skinKey, skinsForItemTag.getBoolean(skinKey));
                }
                this.unlockedSkins.put(itemKey, skinsForItem);
            }
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        if (!SREConfig.instance().isItemSkinEnabled) {
            // 皮肤功能未启用，写入空数据
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            return;
        }
        // 将装备的皮肤数据编码为整数ID，减少网络传输量
        List<Map.Entry<String, String>> validEquipped = new ArrayList<>();
        for (Map.Entry<String, String> entry : equippedSkins.entrySet()) {
            if (SkinManager.getSkinTypeId(entry.getKey()) >= 0
                    && SkinManager.getSkinId(entry.getKey(), entry.getValue()) >= 0) {
                validEquipped.add(entry);
            }
        }
        buf.writeVarInt(validEquipped.size());
        for (Map.Entry<String, String> entry : validEquipped) {
            buf.writeVarInt(SkinManager.getSkinTypeId(entry.getKey()));
            buf.writeVarInt(SkinManager.getSkinId(entry.getKey(), entry.getValue()));
        }

        if (!SREConfig.instance().isItemSkinManagementEnabled) {
            // 皮肤管理功能未启用，写入空的解锁数据
            buf.writeVarInt(0);
            return;
        }
        // 将解锁的皮肤数据编码为整数ID，减少网络传输量
        // 先过滤掉typeId未知的条目
        List<Map.Entry<String, Map<String, Boolean>>> validUnlocked = new ArrayList<>();
        for (Map.Entry<String, Map<String, Boolean>> typeEntry : unlockedSkins.entrySet()) {
            if (SkinManager.getSkinTypeId(typeEntry.getKey()) >= 0) {
                validUnlocked.add(typeEntry);
            }
        }
        buf.writeVarInt(validUnlocked.size());
        for (Map.Entry<String, Map<String, Boolean>> typeEntry : validUnlocked) {
            int typeId = SkinManager.getSkinTypeId(typeEntry.getKey());
            buf.writeVarInt(typeId);
            Map<String, Boolean> typeSkins = typeEntry.getValue();
            List<Integer> validSkinIds = new ArrayList<>();
            for (String skinName : typeSkins.keySet()) {
                int skinId = SkinManager.getSkinId(typeEntry.getKey(), skinName);
                if (skinId >= 0) {
                    validSkinIds.add(skinId);
                }
            }
            buf.writeVarInt(validSkinIds.size());
            for (int skinId : validSkinIds) {
                buf.writeVarInt(skinId);
            }
        }
    }

    @CheckEnvironment(EnvType.CLIENT)
    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        // 读取装备的皮肤数据（整数ID映射解码）
        equippedSkins.clear();
        int equippedCount = buf.readVarInt();
        for (int i = 0; i < equippedCount; i++) {
            int typeId = buf.readVarInt();
            int skinId = buf.readVarInt();
            String typeName = SkinManager.getSkinTypeById(typeId);
            if (typeName != null) {
                String skinName = SkinManager.getSkinById(typeName, skinId);
                if (skinName != null) {
                    equippedSkins.put(typeName, skinName);
                }
            }
        }
        // 读取解锁的皮肤数据（整数ID映射解码）
        unlockedSkins.clear();
        int typeCount = buf.readVarInt();
        for (int i = 0; i < typeCount; i++) {
            int typeId = buf.readVarInt();
            String typeName = SkinManager.getSkinTypeById(typeId);
            int skinCount = buf.readVarInt();
            Map<String, Boolean> skins = new HashMap<>();
            for (int j = 0; j < skinCount; j++) {
                int skinId = buf.readVarInt();
                if (typeName != null) {
                    String skinName = SkinManager.getSkinById(typeName, skinId);
                    if (skinName != null) {
                        skins.put(skinName, true);
                    }
                }
            }
            if (typeName != null) {
                unlockedSkins.put(typeName, skins);
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        if (syncMode) {
            if (!SREConfig.instance().isItemSkinEnabled) {
                return;
            }
        }
        // 写入装备的皮肤数据
        CompoundTag equippedSkinsTag = new CompoundTag();
        for (Map.Entry<String, String> entry : this.equippedSkins.entrySet()) {
            equippedSkinsTag.putString(entry.getKey(), entry.getValue());
        }
        compoundTag.put("equippedSkins", equippedSkinsTag);
        if (this.coinNum > 0) {
            compoundTag.putInt("coinNum", this.coinNum);
        }
        if (this.lootChance > 0) {
            compoundTag.putInt("lootChance", this.lootChance);
        }
        if (syncMode) {
            if (!SREConfig.instance().isItemSkinManagementEnabled) {
                return;
            }
        }
        // 写入解锁的皮肤数据
        CompoundTag unlockedSkinsTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Boolean>> itemEntry : this.unlockedSkins.entrySet()) {
            CompoundTag skinsForItemTag = new CompoundTag();
            for (Map.Entry<String, Boolean> skinEntry : itemEntry.getValue().entrySet()) {
                skinsForItemTag.putBoolean(skinEntry.getKey(), skinEntry.getValue());
            }
            unlockedSkinsTag.put(itemEntry.getKey(), skinsForItemTag);
        }
        compoundTag.put("unlockedSkins", unlockedSkinsTag);
        // compoundTag.putBoolean("isNetworkSyncEnabled", isNetworkSyncEnabled);
    }
}