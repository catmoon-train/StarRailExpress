/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.cca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.exmo.sre.sync.EquippedSkinsDatabaseSync;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
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

import java.util.*;

public class SREPlayerSkinsComponent implements AutoSyncedComponent, ServerTickingComponent {
    private static final Logger logger = LoggerFactory.getLogger(SREPlayerSkinsComponent.class);
    private static final String DATABASE_SYNC_KEY = "skins";
    public static final ComponentKey<SREPlayerSkinsComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("player_skins"),
            SREPlayerSkinsComponent.class);
    public static final ResourceLocation WEAPON_SKINS_DATA_ID = SRE.id("weapon_skins");

    private static final Gson GSON = new GsonBuilder().create();

    private final Player player;
    private Map<String, String> equippedSkins; // 存储当前装备的皮肤 {itemName -> skinName}
    /**
     * 「随机」皮肤的当局解析结果 {itemName -> 实际皮肤名}。
     * <p>
     * 装备值为 {@link ItemSkinManager#RANDOM_SKIN} 时，每局开局由服务端抽取并写入这里，再同步给客户端，
     * 保证同一局内所有端看到的是同一个皮肤。不写入 NBT，属于临时状态。
     */
    private Map<String, String> resolvedRandomSkins;
    private Map<String, Map<String, Boolean>> unlockedSkins; // 存储解锁的皮肤 {itemName -> {skinName -> isUnlocked}}
    private Integer lootChance;
    private Integer coinNum;
    /**
     * 是否把随从展示给其他玩家。
     * <p>
     * 仅在大厅（游戏未开始）生效：对局中所有客户端的可见性判定会无条件屏蔽他人的随从，
     * 因此本字段只是「主人愿意公开」的声明，不是「现在别人能看到」的保证。
     * 默认 {@code false}（只有主人自己可见）。
     */
    private boolean petPublic = false;
    private boolean isNetworkSyncEnabled = false;
    private boolean syncMode = false;
    private volatile boolean databaseSyncQueued = false;
    private volatile long nextDatabaseSyncAt = 0L;

    public SREPlayerSkinsComponent(Player player) {
        this.player = player;
        this.equippedSkins = new HashMap<>();
        this.resolvedRandomSkins = new HashMap<>();
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
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
        return equippedSkins.getOrDefault(itemName, "default");
    }

    /**
     * 设置当前装备的皮肤名称
     */
    public void setEquippedSkin(ItemStack itemStack, String skinName) {
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
        equippedSkins.put(itemName, skinName);
        EquippedSkinsDatabaseSync.queueUpload(this.player);
        markSkinDataChanged();
    }

    /**
     * 解锁一个皮肤
     */
    public void unlockSkin(ItemStack itemStack, String skinName) {
        String itemName =ItemSkinManager.getItemTypeName(itemStack);
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
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
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
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
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
        
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
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
        EquippedSkinsDatabaseSync.queueUpload(this.player);
        markSkinDataChanged();
    }


    /**
     * 是否把随从展示给其他玩家（默认 {@code false}）。
     * <p>
     * 服务端权威；客户端的可见性判定还会额外叠加「游戏是否已开始」与本地客户端配置。
     */
    public boolean isPetPublic() {
        return this.petPublic;
    }

    /**
     * 设置「把随从展示给其他玩家」。
     *
     * @return 是否实际发生了变化（未变化时不会触发同步）
     */
    public boolean setPetPublic(boolean petPublic) {
        if (this.petPublic == petPublic) {
            return false;
        }
        this.petPublic = petPublic;
        markSkinDataChanged();
        return true;
    }
    /**
     * 获取所有装备的皮肤映射
     */
    public Map<String, String> getEquippedSkins() {
        return new HashMap<>(this.equippedSkins);
    }

    /**
     * 获取该物品类型「随机」皮肤的当局解析结果（未抽取时返回 null）。
     */
    public String getResolvedSkin(String itemTypeName) {
        return this.resolvedRandomSkins.get(normalizeItemName(itemTypeName));
    }

    /**
     * 获取所有「随机」皮肤的当局解析结果。
     */
    public Map<String, String> getResolvedSkins() {
        return new HashMap<>(this.resolvedRandomSkins);
    }

    /**
     * 设置该物品类型「随机」皮肤的当局解析结果（服务端抽取后调用，会同步到客户端）。
     */
    public void setResolvedSkin(String itemTypeName, String skinName) {
        String normalizedItemName = normalizeItemName(itemTypeName);
        if (skinName == null || skinName.isBlank()) {
            this.resolvedRandomSkins.remove(normalizedItemName);
        } else {
            this.resolvedRandomSkins.put(normalizedItemName, skinName);
        }
        markSkinDataChanged();
    }

    /**
     * 重新抽取该类型「随机」皮肤（仅服务端有效）。
     *
     * @return 抽中的皮肤名
     */
    public String rollRandomSkin(String itemTypeName) {
        String picked = ItemSkinManager.pickRandomUnlockedSkin(this.player, itemTypeName);
        setResolvedSkin(itemTypeName, picked);
        return picked;
    }

    /**
     * 为该玩家所有已选择「随机」的皮肤类型重新抽取（开局时调用）。
     */
    public void rollAllRandomSkins() {
        for (Map.Entry<String, String> entry : new ArrayList<>(this.equippedSkins.entrySet())) {
            if (ItemSkinManager.isRandomSkin(entry.getValue())) {
                String picked = ItemSkinManager.pickRandomUnlockedSkin(this.player, entry.getKey());
                this.resolvedRandomSkins.put(entry.getKey(), picked);
            }
        }
        markSkinDataChanged();
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
        String itemName = ItemSkinManager.getItemTypeName(itemStack);
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
        KEY.get(player).equippedSkins.put(ItemSkinManager.getItemTypeName(itemStack), skinName);
        EquippedSkinsDatabaseSync.queueUpload(this.player);
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
     * 标记皮肤数据已改变，需要网络同步。
     * 皮肤/经济同步只读策略：仅同步到客户端（this.sync()），不再将本地数据写入远程数据库。
     * 远程数据库由网站端（邮箱/兑换码/抽奖/管理员发放）唯一写入，游戏端只读取。
     */
    private void markSkinDataChanged() {
        this.sync();
    }

    /**
     * 将皮肤数据异步同步到 MySQL —— 已禁用（只读策略）。
     * 保留方法签名以兼容调用方（SkinsNetworkSyncCommand），但不再写入远程数据库。
     */
    public void syncSkinsToNetwork() {
        // 只读策略：不再写入远程数据库。
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
                    serverPlayer.getServer().execute(() -> {
                        if (record != null && record.payload() != null && !record.payload().isBlank()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> skinData = GSON.fromJson(record.payload(), Map.class);
                            if (skinData != null) {
                                this.applyNetworkSkinData(skinData);
                                this.sync();
                                logger.debug("玩家 {} 的皮肤数据已从 MySQL 拉取", this.player.getName().getString());
                            }
                        }
                        if (this.databaseSyncQueued) {
                            // 只读策略：不再将本地数据写入远程数据库。
                            this.databaseSyncQueued = false;
                        }
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("从 MySQL 拉取玩家 {} 的皮肤数据时出错", this.player.getName().getString(), throwable);
                    this.isNetworkSyncEnabled = false;
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
            this.isNetworkSyncEnabled = false;
            logger.error("应用网络皮肤数据时出错", e);
        }
    }

    public boolean flushNetworkSyncBlocking() {
        // 只读策略：关服时不再将本地数据阻塞写入远程数据库。保留签名以兼容调用方。
        return false;
    }

    public void flushNetworkSyncAsyncOnDisconnect() {
        // 只读策略：断线时不再将本地数据异步写入远程数据库。保留签名以兼容调用方。
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
        // 皮肤/经济同步只读策略：游戏端不再将本地皮肤/经济数据写入远程数据库。
        // 远程数据库由网站端（邮箱/兑换码/抽奖/管理员发放）唯一写入，游戏端只读取（pullSkinsFromNetwork）。
        // 保留方法签名以兼容 serverTick / syncSkinsToNetwork 等调用方，但方法体为空操作。
        return false;
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
        // 读取「随机」皮肤的当局解析结果
        if (compoundTag.contains("resolvedRandomSkins")) {
            CompoundTag resolvedTag = compoundTag.getCompound("resolvedRandomSkins");
            this.resolvedRandomSkins.clear();
            for (String key : resolvedTag.getAllKeys()) {
                this.resolvedRandomSkins.put(key, resolvedTag.getString(key));
            }
        }
        // 读取「随从是否展示给其他玩家」
        this.petPublic = compoundTag.contains("petPublic") && compoundTag.getBoolean("petPublic");
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        if (!SREConfig.instance().isItemSkinEnabled) {
            // 皮肤功能未启用，写入空数据（装备 / 解锁 / 随机解析 三段）
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            return;
        }
        // 将装备的皮肤数据编码为整数ID，减少网络传输量
        // 「随机」没有注册皮肤 ID，统一用 -1 编码
        List<Map.Entry<String, String>> validEquipped = new ArrayList<>();
        for (Map.Entry<String, String> entry : equippedSkins.entrySet()) {
            if (ItemSkinManager.getSkinTypeId(entry.getKey()) < 0) {
                continue;
            }
            if (ItemSkinManager.isRandomSkin(entry.getValue())
                    || ItemSkinManager.getSkinId(entry.getKey(), entry.getValue()) >= 0) {
                validEquipped.add(entry);
            }
        }
        buf.writeVarInt(validEquipped.size());
        for (Map.Entry<String, String> entry : validEquipped) {
            buf.writeVarInt(ItemSkinManager.getSkinTypeId(entry.getKey()));
            buf.writeVarInt(ItemSkinManager.isRandomSkin(entry.getValue())
                    ? -1
                    : ItemSkinManager.getSkinId(entry.getKey(), entry.getValue()));
        }

        if (!SREConfig.instance().isItemSkinManagementEnabled) {
            // 皮肤管理功能未启用，写入空的解锁数据 + 空的随机解析
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            return;
        }
        // 将解锁的皮肤数据编码为整数ID，减少网络传输量
        // 先过滤掉typeId未知的条目
        List<Map.Entry<String, Map<String, Boolean>>> validUnlocked = new ArrayList<>();
        for (Map.Entry<String, Map<String, Boolean>> typeEntry : unlockedSkins.entrySet()) {
            if (ItemSkinManager.getSkinTypeId(typeEntry.getKey()) >= 0) {
                validUnlocked.add(typeEntry);
            }
        }
        buf.writeVarInt(validUnlocked.size());
        for (Map.Entry<String, Map<String, Boolean>> typeEntry : validUnlocked) {
            int typeId = ItemSkinManager.getSkinTypeId(typeEntry.getKey());
            buf.writeVarInt(typeId);
            Map<String, Boolean> typeSkins = typeEntry.getValue();
            List<Integer> validSkinIds = new ArrayList<>();
            for (String skinName : typeSkins.keySet()) {
                int skinId = ItemSkinManager.getSkinId(typeEntry.getKey(), skinName);
                if (skinId >= 0) {
                    validSkinIds.add(skinId);
                }
            }
            buf.writeVarInt(validSkinIds.size());
            for (int skinId : validSkinIds) {
                buf.writeVarInt(skinId);
            }
        }

        // 「随机」皮肤的当局解析结果（typeId → skinId），供客户端渲染使用
        List<Map.Entry<String, String>> validResolved = new ArrayList<>();
        for (Map.Entry<String, String> entry : resolvedRandomSkins.entrySet()) {
            if (ItemSkinManager.getSkinTypeId(entry.getKey()) >= 0
                    && ItemSkinManager.getSkinId(entry.getKey(), entry.getValue()) >= 0) {
                validResolved.add(entry);
            }
        }
        buf.writeVarInt(validResolved.size());
        for (Map.Entry<String, String> entry : validResolved) {
            buf.writeVarInt(ItemSkinManager.getSkinTypeId(entry.getKey()));
            buf.writeVarInt(ItemSkinManager.getSkinId(entry.getKey(), entry.getValue()));
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
            String typeName = ItemSkinManager.getSkinTypeById(typeId);
            if (typeName != null) {
                // -1 表示「随机」
                String skinName = skinId < 0
                        ? ItemSkinManager.RANDOM_SKIN
                        : ItemSkinManager.getSkinById(typeName, skinId);
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
            String typeName = ItemSkinManager.getSkinTypeById(typeId);
            int skinCount = buf.readVarInt();
            Map<String, Boolean> skins = new HashMap<>();
            for (int j = 0; j < skinCount; j++) {
                int skinId = buf.readVarInt();
                if (typeName != null) {
                    String skinName = ItemSkinManager.getSkinById(typeName, skinId);
                    if (skinName != null) {
                        skins.put(skinName, true);
                    }
                }
            }
            if (typeName != null) {
                unlockedSkins.put(typeName, skins);
            }
        }
        // 读取「随机」皮肤的当局解析结果
        resolvedRandomSkins.clear();
        int resolvedCount = buf.readVarInt();
        for (int i = 0; i < resolvedCount; i++) {
            int typeId = buf.readVarInt();
            int skinId = buf.readVarInt();
            String typeName = ItemSkinManager.getSkinTypeById(typeId);
            if (typeName == null) {
                continue;
            }
            String skinName = ItemSkinManager.getSkinById(typeName, skinId);
            if (skinName != null) {
                resolvedRandomSkins.put(typeName, skinName);
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
        // 写入「随机」皮肤的当局解析结果（死亡重生 / 存档读回时保持一致，开局会重新抽取）
        CompoundTag resolvedRandomSkinsTag = new CompoundTag();
        for (Map.Entry<String, String> entry : this.resolvedRandomSkins.entrySet()) {
            resolvedRandomSkinsTag.putString(entry.getKey(), entry.getValue());
        }
        compoundTag.put("resolvedRandomSkins", resolvedRandomSkinsTag);
        // 写入「随从是否展示给其他玩家」（默认 false 不占包体）
        if (this.petPublic) {
            compoundTag.putBoolean("petPublic", true);
        }
        // compoundTag.putBoolean("isNetworkSyncEnabled", isNetworkSyncEnabled);
    }
}
