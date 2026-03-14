package io.wifi.starrailexpress.cca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.syncrequests.SyncRequests;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SREPlayerSkinsComponent implements AutoSyncedComponent, ServerTickingComponent {
    private static final Logger logger = LoggerFactory.getLogger(SREPlayerSkinsComponent.class);
    public static final ComponentKey<SREPlayerSkinsComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("player_skins"),
            SREPlayerSkinsComponent.class);
    public static final ResourceLocation WEAPON_SKINS_DATA_ID = SRE.id("weapon_skins");

    private static final Gson GSON = new GsonBuilder().create();

    private final Player player;
    private Map<String, String> equippedSkins; // 存储当前装备的皮肤 {itemName -> skinName}
    private Map<String, Map<String, Boolean>> unlockedSkins; // 存储解锁的皮肤 {itemName -> {skinName -> isUnlocked}}

    // HTTP 网络同步管理器
    public static SyncRequests syncRequests = null;
    // private long lastNetworkSync = 0;
    // private static final long NETWORK_SYNC_INTERVAL = 20000; // 每 20 秒同步一次到网络
    private boolean isNetworkSyncEnabled = false;

    public SREPlayerSkinsComponent(Player player) {
        this.player = player;
        this.equippedSkins = new HashMap<>();
        this.unlockedSkins = new HashMap<>();
    }

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        return this.player == serverPlayer;
    }

    /**
     * 初始化网络同步
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     */
    public void initializeNetworkSync(String host, int port, String key) {
        if (syncRequests == null) {
            try {
                String baseUrl = "http://" + host + ":" + port;
                syncRequests = new SyncRequests(baseUrl, key);
                this.isNetworkSyncEnabled = true;
                logger.info("玩家 {} 的皮肤网络同步已启用 (SyncRequests): {}", this.player.getName().getString(), baseUrl);
            } catch (Exception e) {
                logger.error("玩家 {} 的皮肤网络同步初始化失败", this.player.getName().getString(), e);
                this.isNetworkSyncEnabled = false;
            }
        } else {
            this.isNetworkSyncEnabled = true;
        }
    }

    /**
     * 禁用全局网络同步
     */
    public static void disableGlobalNetworkSync() {
        syncRequests = null;
    }
    /**
     * 禁用网络同步
     */
    public void disableNetworkSync() {
        this.isNetworkSyncEnabled = false;
    }

    @Override
    public void serverTick() {
        // 定期同步皮肤数据到服务器
        // if (this.isNetworkSyncEnabled && this.player.getServer() != null) {
        // long currentTime = System.currentTimeMillis();
        // if (currentTime - this.lastNetworkSync >= NETWORK_SYNC_INTERVAL) {
        // this.lastNetworkSync = currentTime;
        // this.pullSkinsFromNetwork();
        // }
        // }
    }

    /**
     * 获取当前装备的皮肤名称
     */
    public String getEquippedSkin(ItemStack itemStack) {
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString().toLowerCase();
        return equippedSkins.getOrDefault(itemName, "default");
    }

    /**
     * 设置当前装备的皮肤名称
     */
    public void setEquippedSkin(ItemStack itemStack, String skinName) {
        String itemName = itemStack.getItem().toString().toLowerCase();
        equippedSkins.put(itemName, skinName);
    }

    /**
     * 解锁一个皮肤
     */
    public void unlockSkin(ItemStack itemStack, String skinName) {
        String itemName = itemStack.getItem().toString().toLowerCase();
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
        String itemName = itemStack.getItem().toString().toLowerCase();
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
        // 触发网络同步
        markSkinDataChanged();
    }

    /**
     * 检查皮肤是否已解锁
     */
    public boolean isSkinUnlocked(ItemStack itemStack, String skinName) {
        String itemName = itemStack.getItem().toString().toLowerCase();
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
        String itemName = itemStack.getItem().toString().toLowerCase();
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
    }

    /**
     * 获取指定物品类型的当前装备皮肤
     */
    public String getEquippedSkinForItemType(String itemTypeName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        return equippedSkins.getOrDefault(normalizedItemName, "default");
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

        // 使用物品的注册名而不是显示名称，以确保一致性
        String itemName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

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
        KEY.get(player).equippedSkins.put(BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString(), skinName);
        sync();

    }

    /**
     * 标准化物品名称
     */
    private String normalizeItemName(String itemTypeName) {
        // 将物品类型名称标准化为小写，去除空格等
        return itemTypeName.toLowerCase().trim().replaceAll("[^a-z0-9_:]", "");
    }

    /**
     * 标记皮肤数据已改变，需要网络同步
     */
    private void markSkinDataChanged() {
        // 同步到本地客户端
        this.sync();
        // 设置网络同步标志，下一个服务器刻度时会同步
        // this.lastNetworkSync = 0;
    }

    /**
     * 将皮肤数据异步同步到 HTTP 网络服务器
     */
    public void syncSkinsToNetwork() {
        if (!this.isNetworkSyncEnabled || syncRequests == null) {
            return;
        }

        try {
            // 异步执行网络同步，不阻塞游戏线程
            Thread syncThread = new Thread(() -> {
                try {
                    // 创建皮肤数据对象
                    Map<String, Object> skinData = new HashMap<>();
                    skinData.put("equipped", this.equippedSkins);
                    skinData.put("unlocked", this.deepCopyUnlockedSkins());
                    skinData.put("version", System.currentTimeMillis());
                    skinData.put("timestamp", System.currentTimeMillis());

                    String skinDataJson = GSON.toJson(skinData);

                    // 使用 SyncRequests 发送 POST 请求
                    boolean success = syncRequests.setValue(
                            this.player.getUUID(),
                            "skins",
                            skinDataJson);

                    if (success) {
                        logger.debug("成功同步皮肤数据到服务器，玩家：{}", this.player.getName().getString());
                    } else {
                        logger.warn("同步皮肤数据到服务器失败，玩家：{}", this.player.getName().getString());
                    }
                } catch (Exception e) {
                    logger.error("提交皮肤数据同步任务时出错，玩家：{}", this.player.getName().getString(), e);
                }
            });
            syncThread.setName("SkinSync-" + this.player.getStringUUID());
            syncThread.setDaemon(true);
            syncThread.start();
        } catch (Exception e) {
            logger.error("提交皮肤数据同步任务时出错，玩家：{}", this.player.getName().getString(), e);
        }
    }

    /**
     * 从网络服务器异步拉取皮肤数据
     */
    public void pullSkinsFromNetwork() {
        if (!SREConfig.itemSkinSyncServerEnabled)
            return;
        if (!this.isNetworkSyncEnabled || syncRequests == null) {
            return;
        }

        try {
            // 异步拉取，并在完成时应用数据
            Thread fetchThread = new Thread(() -> {
                try {
                    String responseJson = syncRequests.getValue(
                            this.player.getUUID(),
                            "skins");

                    if (responseJson != null && !responseJson.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> skinData = GSON.fromJson(responseJson, Map.class);
                        if (skinData != null) {
                            this.applyNetworkSkinData(skinData);
                            this.sync();
                            logger.debug("玩家 {} 的皮肤数据已从网络拉取", this.player.getName().getString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("从网络拉取玩家 {} 的皮肤数据时出错", this.player.getName().getString(), e);
                }
            });
            fetchThread.setName("SkinFetch-" + this.player.getStringUUID());
            fetchThread.setDaemon(true);
            fetchThread.start();
        } catch (Exception e) {
            logger.error("从网络拉取玩家 {} 的皮肤数据时出错", this.player.getName().getString(), e);
        }
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

            if (skinData.containsKey("version") && skinData.get("version") instanceof Number) {
                // long version = ((Number) skinData.get("version")).longValue();
                // SyncRequests 不需要版本号，忽略
            }
        } catch (Exception e) {
            logger.error("应用网络皮肤数据时出错", e);
        }
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
     * 获取网络同步管理器
     */
    public SyncRequests getNetworkSyncManager() {
        return syncRequests;
    }

    /**
     * 检查网络同步是否已启用
     */
    public boolean isNetworkSyncEnabled() {
        return this.isNetworkSyncEnabled;
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        // 读取装备的皮肤数据
        CompoundTag equippedSkinsTag = compoundTag.getCompound("equippedSkins");
        for (String key : equippedSkinsTag.getAllKeys()) {
            this.equippedSkins.put(key, equippedSkinsTag.getString(key));
        }

        // 读取解锁的皮肤数据
        CompoundTag unlockedSkinsTag = compoundTag.getCompound("unlockedSkins");
        for (String itemKey : unlockedSkinsTag.getAllKeys()) {
            CompoundTag skinsForItemTag = unlockedSkinsTag.getCompound(itemKey);
            Map<String, Boolean> skinsForItem = new HashMap<>();
            for (String skinKey : skinsForItemTag.getAllKeys()) {
                skinsForItem.put(skinKey, skinsForItemTag.getBoolean(skinKey));
            }
            this.unlockedSkins.put(itemKey, skinsForItem);
        }
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        // 写入装备的皮肤数据
        CompoundTag equippedSkinsTag = new CompoundTag();
        for (Map.Entry<String, String> entry : this.equippedSkins.entrySet()) {
            equippedSkinsTag.putString(entry.getKey(), entry.getValue());
        }
        compoundTag.put("equippedSkins", equippedSkinsTag);

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