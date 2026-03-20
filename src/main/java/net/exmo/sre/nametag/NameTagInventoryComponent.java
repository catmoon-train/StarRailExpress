package net.exmo.sre.nametag;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.syncrequests.SyncRequests;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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

    public static final ComponentKey<NameTagInventoryComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("nametag_inventory"), NameTagInventoryComponent.class);

    private final Player player;
    public ArrayList<String> nameTags = new ArrayList<>();
    public String CurrentNameTag = "";
    
    // HTTP 网络同步管理器
    public static SyncRequests syncRequests = null;
    private boolean isNetworkSyncEnabled = false;
    public NameTagInventoryComponent(Player player) {
        this.player = player;
    }
    
    /**
     * 初始化网络同步
     * 
     * @param host 服务器主机地址
     * @param port 服务器端口
     * @param key 认证密钥
     */
    public void initializeNetworkSync(String host, int port, String key) {
        if (syncRequests == null) {
            try {
                String baseUrl = "http://" + host + ":" + port;
                syncRequests = new SyncRequests(baseUrl, key);
                this.isNetworkSyncEnabled = true;
                logger.info("玩家 {} 的名片网络同步已启用 (SyncRequests): {}", this.player.getName().getString(), baseUrl);
            } catch (Exception e) {
                logger.error("玩家 {} 的名片网络同步初始化失败", this.player.getName().getString(), e);
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

    public void syncFromLinkedServer(){
        if (!SREConfig.instance().itemSkinSyncServerEnabled)
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
                            "nametags");
    
                    if (responseJson != null && !responseJson.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nametagData = GSON.fromJson(responseJson, Map.class);
                        if (nametagData != null) {
                            this.applyNetworkNametagData(nametagData);
                            this.sync();
                            logger.debug("玩家 {} 的名片数据已从网络拉取", this.player.getName().getString());
                        }
                    }
                } catch (Exception e) {
                    logger.error("从网络拉取玩家 {} 的名片数据时出错", this.player.getName().getString(), e);
                }
            });
            fetchThread.setName("NametagFetch-" + this.player.getStringUUID());
            fetchThread.setDaemon(true);
            fetchThread.start();
        } catch (Exception e) {
            logger.error("从网络拉取玩家 {} 的名片数据时出错", this.player.getName().getString(), e);
        }
    }
    
    public MutableComponent generate() {
        var nameTag = Component.literal("");
        if (getPlayer().isSpectator()){
            nameTag.append(Component.translatable("starrailexpress.tag.spectator").append(" "));
        }
        MutableComponent translatable = Component.translatable( CurrentNameTag);
        nameTag.append(translatable).append(" ");
        return nameTag;
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
     * 将名片数据异步同步到 HTTP 网络服务器
     */
    public void syncToNetwork() {
        if (!this.isNetworkSyncEnabled || syncRequests == null) {
            return;
        }

        try {
            // 异步执行网络同步，不阻塞游戏线程
            Thread syncThread = new Thread(() -> {
                try {
                    // 创建名片数据对象
                    Map<String, Object> nametagData = new HashMap<>();
                    nametagData.put("nameTags", new ArrayList<>(this.nameTags));
                    nametagData.put("currentNametag", this.CurrentNameTag);
                    nametagData.put("version", System.currentTimeMillis());
                    nametagData.put("timestamp", System.currentTimeMillis());

                    String nametagDataJson = GSON.toJson(nametagData);

                    // 使用 SyncRequests 发送 POST 请求
                    boolean success = syncRequests.setValue(
                            this.player.getUUID(),
                            "nametags",
                            nametagDataJson);

                    if (success) {
                        logger.debug("成功同步名片数据到服务器，玩家：{}", this.player.getName().getString());
                    } else {
                        logger.warn("同步名片数据到服务器失败，玩家：{}", this.player.getName().getString());
                    }
                } catch (Exception e) {
                    logger.error("提交名片数据同步任务时出错，玩家：{}", this.player.getName().getString(), e);
                }
            });
            syncThread.setName("NametagSync-" + this.player.getStringUUID());
            syncThread.setDaemon(true);
            syncThread.start();
        } catch (Exception e) {
            logger.error("提交名片数据同步任务时出错，玩家：{}", this.player.getName().getString(), e);
        }
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

            if (nametagData.containsKey("version") && nametagData.get("version") instanceof Number) {
                // 版本号暂时不使用，忽略
            }
        } catch (Exception e) {
            logger.error("应用网络名片数据时出错", e);
        }
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
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromSyncNbt(tag, registryLookup);
    }
}
