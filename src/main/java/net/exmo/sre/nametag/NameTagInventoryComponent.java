package net.exmo.sre.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NameTagInventoryComponent implements RoleComponent {
    private static final Logger logger = LoggerFactory.getLogger(NameTagInventoryComponent.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DATABASE_SYNC_KEY = "nametags";
    private static final long DATABASE_SYNC_FLUSH_TIMEOUT_MS = 4000L;

    public static final ComponentKey<NameTagInventoryComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("nametag_inventory"), NameTagInventoryComponent.class);

    private final Player player;
    public ArrayList<String> nameTags = new ArrayList<>();
    public String CurrentNameTag = "";

    private boolean isNetworkSyncEnabled = false;

    public NameTagInventoryComponent(Player player) {
        this.player = player;
    }

    /**
     * 初始化网络同步
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @param key  认证密钥
     */
    public void initializeNetworkSync(String host, int port, String key) {
        this.isNetworkSyncEnabled = SREConfig.instance().itemSkinSyncServerEnabled
                && SREConfig.instance().mysqlPlayerSyncEnabled
                && MysqlPlayerDataStore.isAvailable();
        if (this.isNetworkSyncEnabled) {
            logger.info("玩家 {} 的名片 MySQL 同步已启用", this.player.getName().getString());
        } else if (SREConfig.instance().itemSkinSyncServerEnabled) {
            logger.warn("玩家 {} 的名片 MySQL 同步未启用，数据库不可用或配置未完成。", this.player.getName().getString());
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

    public void syncFromLinkedServer() {
        if (!SREConfig.instance().itemSkinSyncServerEnabled)
            return;
        if (!this.isNetworkSyncEnabled || !(this.player instanceof ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) {
            return;
        }

        MysqlPlayerDataStore.loadBatchAsync(this.player.getUUID(), List.of(DATABASE_SYNC_KEY))
                .thenAccept(records -> {
                    MysqlPlayerDataStore.SyncRecord record = records.get(DATABASE_SYNC_KEY);
                    if (record == null || record.payload() == null || record.payload().isBlank()) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nametagData = GSON.fromJson(record.payload(), Map.class);
                    if (nametagData == null) {
                        return;
                    }
                    serverPlayer.getServer().execute(() -> {
                        this.applyNetworkNametagData(nametagData);
                        this.sync();
                        logger.debug("玩家 {} 的名片数据已从 MySQL 拉取", this.player.getName().getString());
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("从 MySQL 拉取玩家 {} 的名片数据时出错", this.player.getName().getString(), throwable);
                    this.isNetworkSyncEnabled = false;
                    // 出错不同步
                    return null;
                });
    }

    public MutableComponent generate() {
        ArrayList<MutableComponent> toAddNameTags = new ArrayList<>();
        if (getPlayer().isSpectator()) {
            toAddNameTags.add(Component.translatable("starrailexpress.tag.spectator"));
        }
        // ComponentUtils.formatList(toAddNameTags);
        if (CurrentNameTag != null && !CurrentNameTag.isEmpty() && !CurrentNameTag.isBlank()) {
            toAddNameTags.add(Component.translatable(CurrentNameTag));
        }
        if (!toAddNameTags.isEmpty()) {
            return ComponentUtils.formatList(toAddNameTags, Component.literal(" "), (t) -> {
                return t;
            }).copy().append(" ");
        }
        return null;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        nameTags.clear();
        nameTags.addAll(compoundTag.getList("nameTags", 8).stream().map(Tag::getAsString).toList());

        CurrentNameTag = compoundTag.getString("CurrentNameTag");
    }

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {
        // 保存 nameTags 列表
        ListTag nameTagsList = new ListTag();
        for (String nameTag : nameTags) {
            nameTagsList.add(StringTag.valueOf(nameTag));
        }
        compoundTag.put("nameTags", nameTagsList);

        // 保存当前选中的名片
        compoundTag.putString("CurrentNameTag", CurrentNameTag);
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        this.nameTags.clear();
        this.CurrentNameTag = "";
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        KEY.sync(this.player);
    }

    /**
     * 添加名片
     */
    public void addNameTag(String nameTag) {
        if (!nameTags.contains(nameTag)) {
            nameTags.add(nameTag);
            this.sync();
            // 触发网络同步
            syncToNetwork();
        }
    }

    /**
     * 移除名片
     */
    public void removeNameTag(String nameTag) {
        if (nameTags.remove(nameTag)) {
            // 如果移除的是当前选中的名片，清空当前选中
            if (nameTag.equals(CurrentNameTag)) {
                CurrentNameTag = "";
            }
            this.sync();
            // 触发网络同步
            syncToNetwork();
        }
    }

    /**
     * 设置当前名片
     */
    public void setCurrentNameTag(String nameTag) {
        if (nameTags.contains(nameTag)) {
            CurrentNameTag = nameTag;
            this.sync();
            // 触发网络同步
            syncToNetwork();
        }
    }

    /**
     * 获取当前名片
     */
    public String getCurrentNameTag() {
        return CurrentNameTag;
    }

    /**
     * 将名片数据异步同步到 MySQL
     */
    public void syncToNetwork() {
        if (!this.isNetworkSyncEnabled) {
            return;
        }

        MysqlPlayerDataStore.saveBatchAsync(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis())
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.warn("异步同步玩家 {} 的名片数据到 MySQL 失败。", this.player.getName().getString(), throwable);
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        logger.warn("异步同步玩家 {} 的名片数据到 MySQL 未成功写入。", this.player.getName().getString());
                    }
                });
    }

    /**
     * 应用从网络获取的名片数据
     */
    private void applyNetworkNametagData(Map<String, Object> nametagData) {
        try {
            if (nametagData.containsKey("nameTags")) {
                Object nameTagsObj = nametagData.get("nameTags");
                if (nameTagsObj instanceof List) {
                    List<?> rawList = (List<?>) nameTagsObj;
                    List<String> newNameTags = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof String) {
                            newNameTags.add((String) item);
                        }
                    }
                    this.nameTags.clear();
                    this.nameTags.addAll(newNameTags);
                }
            }

            if (nametagData.containsKey("currentNametag")) {
                Object currentNametag = nametagData.get("currentNametag");
                if (currentNametag instanceof String) {
                    this.CurrentNameTag = (String) currentNametag;
                }
            }

        } catch (Exception e) {
            logger.error("应用网络名片数据时出错", e);
        }
    }

    public boolean flushNetworkSyncBlocking() {
        if (!this.isNetworkSyncEnabled) {
            return false;
        }
        return MysqlPlayerDataStore.saveBatchBlocking(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis(),
                DATABASE_SYNC_FLUSH_TIMEOUT_MS);
    }

    public void flushNetworkSyncAsyncOnDisconnect() {
        if (!this.isNetworkSyncEnabled) {
            return;
        }

        MysqlPlayerDataStore.saveBatchAsync(
                this.player.getUUID(),
                Map.of(DATABASE_SYNC_KEY, GSON.toJson(buildNametagPayload())),
                System.currentTimeMillis())
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        logger.warn("断线时异步同步玩家 {} 的名片数据到 MySQL 失败。", this.player.getName().getString(), throwable);
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        logger.warn("断线时异步同步玩家 {} 的名片数据到 MySQL 未成功写入。", this.player.getName().getString());
                    }
                });
    }

    /**
     * 检查网络同步是否已启用
     */
    public boolean isNetworkSyncEnabled() {
        return this.isNetworkSyncEnabled;
    }

    private Map<String, Object> buildNametagPayload() {
        Map<String, Object> nametagData = new HashMap<>();
        nametagData.put("nameTags", new ArrayList<>(this.nameTags));
        nametagData.put("currentNametag", this.CurrentNameTag);
        nametagData.put("version", System.currentTimeMillis());
        nametagData.put("timestamp", System.currentTimeMillis());
        return nametagData;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
