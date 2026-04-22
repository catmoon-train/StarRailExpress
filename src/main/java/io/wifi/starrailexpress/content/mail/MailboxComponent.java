package io.wifi.starrailexpress.content.mail;

import com.google.gson.*;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 邮箱组件 – 挂载到每个玩家身上。
 * <p>
 * 功能：
 * <ul>
 *     <li>存储玩家的邮件列表</li>
 *     <li>自动同步到客户端（CCA AutoSync）</li>
 *     <li>定时同步到 MySQL 数据库</li>
 *     <li>支持领取附件 + 执行指令、标记已读、删除已读</li>
 *     <li>网络优化：脏标记 + 20 tick 批量同步</li>
 * </ul>
 */
public class MailboxComponent implements AutoSyncedComponent, ServerTickingComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxComponent.class);
    private static final Gson GSON = new GsonBuilder().create();

    public static final ComponentKey<MailboxComponent> KEY =
            ComponentRegistry.getOrCreate(SRE.id("mailbox"), MailboxComponent.class);

    /** MySQL data_key */
    private static final String DB_KEY = "mailbox";
    /** 最大邮件数量 */
    private static final int MAX_MAILS = 100;
    /** 同步间隔（ticks） */
    private static final int SYNC_INTERVAL = 20;

    private final Player player;
    private final List<Mail> mails = new ArrayList<>();

    // -- dirty tracking --
    private boolean dirty = false;
    private int tickCounter = 0;
    private boolean dbLoaded = false;
    private boolean databaseLoadPending = false;

    public MailboxComponent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }

    // =========================================================================
    // 公开 API
    // =========================================================================

    /** 获取邮件列表（只读视图） */
    public List<Mail> getMails() {
        return java.util.Collections.unmodifiableList(mails);
    }

    /** 未读邮件数量 */
    public int getUnreadCount() {
        int count = 0;
        for (Mail m : mails) {
            if (!m.read && !m.isExpired()) count++;
        }
        return count;
    }

    /** 可领取邮件数量 */
    public int getClaimableCount() {
        int count = 0;
        for (Mail m : mails) {
            if (m.hasRewards() && !m.claimed && !m.isExpired()) count++;
        }
        return count;
    }

    /** 发送一封新邮件给这个玩家（服务端调用） */
    public void sendMail(Mail mail) {
        if (mails.size() >= MAX_MAILS) {
            // 移除最早的已领取或过期邮件
            pruneOldest();
        }
        if (mails.size() >= MAX_MAILS) {
            LOGGER.warn("Player {} mailbox full, dropping mail {}", player.getName().getString(), mail.id);
            return;
        }
        mails.add(mail);
        markDirty();
    }

    /** 标记邮件为已读 */
    public void markRead(UUID mailId) {
        for (Mail m : mails) {
            if (m.id.equals(mailId) && !m.read) {
                m.read = true;
                markDirty();
                return;
            }
        }
    }

    /** 领取邮件附件 & 执行指令 */
    public boolean claimMail(UUID mailId) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        for (Mail m : mails) {
            if (m.id.equals(mailId) && !m.claimed && m.hasRewards() && !m.isExpired()) {
                // 发放附件
                for (ItemStack stack : m.attachments) {
                    ItemStack copy = stack.copy();
                    if (!serverPlayer.getInventory().add(copy)) {
                        // 背包满了，丢到地上
                        serverPlayer.drop(copy, false);
                    }
                }
                // 执行指令
                executeClaimCommands(serverPlayer, m.claimCommands);
                m.claimed = true;
                m.read = true;
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** 一键领取所有可领取邮件 */
    public int claimAll() {
        if (!(player instanceof ServerPlayer)) return 0;
        int count = 0;
        for (Mail m : mails) {
            if (!m.claimed && m.hasRewards() && !m.isExpired()) {
                if (claimMail(m.id)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 删除一封邮件（必须已领取或无附件） */
    public boolean deleteMail(UUID mailId) {
        Iterator<Mail> it = mails.iterator();
        while (it.hasNext()) {
            Mail m = it.next();
            if (m.id.equals(mailId) && m.canDelete()) {
                it.remove();
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** 删除所有已领取/已读邮件 */
    public int deleteAllClaimed() {
        int count = 0;
        Iterator<Mail> it = mails.iterator();
        while (it.hasNext()) {
            Mail m = it.next();
            if (m.canDelete() && m.read) {
                it.remove();
                count++;
            }
        }
        if (count > 0) markDirty();
        return count;
    }

    /** 清空所有邮件（管理员操作） */
    public void clearAllMails() {
        if (!mails.isEmpty()) {
            mails.clear();
            markDirty();
        }
    }

    // =========================================================================
    // 内部方法
    // =========================================================================

    private void markDirty() {
        dirty = true;
    }

    private void pruneOldest() {
        // 先移除已过期的
        mails.removeIf(Mail::isExpired);
        // 如果还超了，移除最早的已领取邮件
        if (mails.size() >= MAX_MAILS) {
            mails.removeIf(m -> m.claimed);
        }
    }

    private void executeClaimCommands(ServerPlayer serverPlayer, List<String> commands) {
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return;
        String playerName = serverPlayer.getName().getString();
        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        for (String cmd : commands) {
            String resolved = cmd.replace("{player}", playerName);
            try {
                server.getCommands().performPrefixedCommand(source, resolved);
            } catch (Exception e) {
                LOGGER.warn("Failed to execute mail claim command '{}' for player {}",
                        resolved, playerName, e);
            }
        }
    }

    // =========================================================================
    // CCA sync
    // =========================================================================

    public void sync() {
        KEY.sync(this.player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        CompoundTag tag = new CompoundTag();
        writeToSyncNbt(tag, buf.registryAccess());
        buf.writeNbt(tag);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag != null) {
            readFromSyncNbt(tag, buf.registryAccess());
        }
    }

    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Mail m : mails) {
            if (!m.isExpired()) {
                list.add(m.toNbt(provider));
            }
        }
        tag.put("Mails", list);
    }

    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        mails.clear();
        ListTag list = tag.getList("Mails", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            mails.add(Mail.fromNbt(list.getCompound(i), provider));
        }
    }

    // =========================================================================
    // NBT 持久化（级别存储）
    // =========================================================================

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        mails.clear();
        if (tag.contains("Mails", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Mails", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Mail m = Mail.fromNbt(list.getCompound(i), provider);
                if (!m.isExpired()) {
                    mails.add(m);
                }
            }
        }
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Mail m : mails) {
            if (!m.isExpired()) {
                list.add(m.toNbt(provider));
            }
        }
        tag.put("Mails", list);
    }

    // =========================================================================
    // Server Tick – 脏数据批量同步
    // =========================================================================

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer)) return;

        // 首次加载尝试从 MySQL 读取
        if (!dbLoaded) {
            dbLoaded = true;
            loadFromDatabase();
        }

        tickCounter++;
        if (tickCounter >= SYNC_INTERVAL && dirty) {
            tickCounter = 0;
            sync();
            if (saveToDatabase()) {
                dirty = false;
            }
        }
    }

    // =========================================================================
    // MySQL 同步
    // =========================================================================

    private void loadFromDatabase() {
        if (!MysqlPlayerDataStore.isAvailable()) return;
        this.databaseLoadPending = true;
        MysqlPlayerDataStore.loadBatchAsync(player.getUUID(), List.of(DB_KEY))
                .thenAccept(records -> {
                    MysqlPlayerDataStore.SyncRecord record = records.get(DB_KEY);
                    if (player instanceof ServerPlayer sp && sp.getServer() != null) {
                        sp.getServer().execute(() -> {
                            this.databaseLoadPending = false;
                            if (record != null && record.payload() != null && !record.payload().isEmpty()) {
                                try {
                                    List<Mail> loaded = deserializeFromJson(record.payload());
                                    mergeFromDatabase(loaded);
                                    sync();
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to load mailbox from DB for player {}",
                                            player.getName().getString(), e);
                                }
                            }
                        });
                    }
                })
                .exceptionally(throwable -> {
                    this.databaseLoadPending = false;
                    LOGGER.warn("Failed to load mailbox from DB for player {}",
                            player.getName().getString(), throwable);
                    return null;
                });
    }

    private void mergeFromDatabase(List<Mail> loaded) {
        boolean changed = false;
        for (Mail dbMail : loaded) {
            if (dbMail.isExpired()) continue;
            boolean exists = false;
            for (Mail local : mails) {
                if (local.id.equals(dbMail.id)) {
                    exists = true;
                    // DB 中更新的状态覆盖本地
                    if (dbMail.claimed && !local.claimed) {
                        local.claimed = true;
                        changed = true;
                    }
                    if (dbMail.read && !local.read) {
                        local.read = true;
                        changed = true;
                    }
                    break;
                }
            }
            if (!exists && mails.size() < MAX_MAILS) {
                mails.add(dbMail);
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    private boolean saveToDatabase() {
        if (!MysqlPlayerDataStore.isAvailable() || this.databaseLoadPending) return false;
        String json = serializeToJson();
        long now = System.currentTimeMillis();
        MysqlPlayerDataStore.saveBatchAsync(player.getUUID(), Map.of(DB_KEY, json), now)
                .exceptionally(ex -> {
                    LOGGER.warn("Failed to save mailbox to DB for player {}",
                            player.getName().getString(), ex);
                    return false;
                });
        return true;
    }

    private String serializeToJson() {
        JsonArray arr = new JsonArray();
        for (Mail m : mails) {
            if (!m.isExpired()) {
                arr.add(JsonParser.parseString(m.toJson()));
            }
        }
        return GSON.toJson(arr);
    }

    private List<Mail> deserializeFromJson(String json) {
        List<Mail> result = new ArrayList<>();
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement e : arr) {
            try {
                result.add(Mail.fromJsonMeta(e.toString()));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse mail from JSON", ex);
            }
        }
        return result;
    }
}
